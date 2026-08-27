package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.WorkplaceRequest;
import banghak.home.halley.config.exception.AuthenticationRequiredException;
import banghak.home.halley.config.exception.DuplicateEmailException;
import banghak.home.halley.config.exception.DuplicateNicknameException;
import banghak.home.halley.config.exception.LastAdminException;
import banghak.home.halley.config.exception.NotFoundUserException;
import banghak.home.halley.config.exception.SelfDeleteException;
import banghak.home.halley.config.exception.SelfDisableException;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse me() {
        return toResponse(get(currentUserId()));
    }

    public UserResponse updateWorkplace(WorkplaceRequest request) {
        final User user = get(currentUserId());
        final User updated = userRepository.update(new User(
                user.id(), user.nickname(), user.email(), user.passwordHash(), user.role(),
                request.workplaceName(), request.workplaceLat(), request.workplaceLng(),
                user.mustChangePassword(), user.availableBudget(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        return toResponse(updated);
    }

    private Long currentUserId() {
        final Long id = currentAdminId();
        if (id == null) {
            throw new AuthenticationRequiredException();
        }
        return id;
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }
        if (userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User saved = userRepository.save(new User(
                null,
                request.nickname(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role() == null ? UserRole.MEMBER : request.role(),
                request.workplaceName(),
                request.workplaceLat(),
                request.workplaceLng(),
                true,
                request.availableBudget() == null ? 0L : request.availableBudget(),
                true,
                null, null, null));
        return toResponse(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        final User user = get(id);
        if (!user.email().equals(request.email()) && userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }
        if (!user.nickname().equals(request.nickname()) && userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User updated = userRepository.update(new User(
                user.id(),
                request.nickname(),
                request.email(),
                user.passwordHash(),
                user.role(),
                request.workplaceName(),
                request.workplaceLat(),
                request.workplaceLng(),
                user.mustChangePassword(),
                request.availableBudget() == null ? user.availableBudget() : request.availableBudget(),
                user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        return toResponse(updated);
    }

    public void delete(Long id) {
        final User user = get(id);
        if (user.id().equals(currentAdminId())) {
            throw new SelfDeleteException();
        }
        if (user.role() == UserRole.ADMIN && countAdmins() <= 1) {
            throw new LastAdminException("마지막 관리자는 삭제할 수 없습니다");
        }
        userRepository.delete(id);
    }

    public UserResponse updateStatus(Long id, boolean enabled) {
        final User user = get(id);
        if (!enabled) {
            if (user.id().equals(currentAdminId())) {
                throw new SelfDisableException();
            }
            if (user.role() == UserRole.ADMIN && countAdmins() <= 1) {
                throw new LastAdminException("마지막 관리자는 비활성화할 수 없습니다");
            }
        }
        final Instant now = Instant.now();
        final User updated = userRepository.update(new User(
                user.id(), user.nickname(), user.email(), user.passwordHash(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.mustChangePassword(), user.availableBudget(), enabled,
                enabled ? null : now,
                enabled ? null : currentAdminId(),
                user.createdAt()));
        return toResponse(updated);
    }

    public ResetPasswordResponse resetPassword(Long id) {
        final User user = get(id);
        final String temporaryPassword = randomPassword();
        userRepository.update(new User(
                user.id(), user.nickname(), user.email(), passwordEncoder.encode(temporaryPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                true, user.availableBudget(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        return new ResetPasswordResponse(temporaryPassword);
    }

    private User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(NotFoundUserException::new);
    }

    private long countAdmins() {
        return userRepository.findAll().stream().filter(u -> u.role() == UserRole.ADMIN).count();
    }

    private Long currentAdminId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.id(), user.nickname(), user.email(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.availableBudget(), user.enabled(), user.mustChangePassword(), user.createdAt());
    }

    private String randomPassword() {
        final SecureRandom random = new SecureRandom();
        final StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
