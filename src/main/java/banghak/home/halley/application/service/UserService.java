package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.ApiException;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.springframework.http.HttpStatus;
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

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "이미 존재하는 이메일입니다");
        }
        if (userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_DUPLICATED", "이미 존재하는 닉네임입니다");
        }
        User saved = userRepository.save(new User(
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
        User user = get(id);
        if (!user.email().equals(request.email()) && userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "이미 존재하는 이메일입니다");
        }
        if (!user.nickname().equals(request.nickname()) && userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_DUPLICATED", "이미 존재하는 닉네임입니다");
        }
        User updated = userRepository.update(new User(
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
        User user = get(id);
        if (user.id().equals(currentAdminId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_DELETE", "자기 자신을 삭제할 수 없습니다");
        }
        if (user.role() == UserRole.ADMIN && countAdmins() <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_ADMIN", "마지막 관리자는 삭제할 수 없습니다");
        }
        userRepository.delete(id);
    }

    public UserResponse updateStatus(Long id, boolean enabled) {
        User user = get(id);
        if (!enabled) {
            if (user.id().equals(currentAdminId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_DISABLE", "자기 자신을 비활성화할 수 없습니다");
            }
            if (user.role() == UserRole.ADMIN && countAdmins() <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_ADMIN", "마지막 관리자는 비활성화할 수 없습니다");
            }
        }
        Instant now = Instant.now();
        User updated = userRepository.update(new User(
                user.id(), user.nickname(), user.email(), user.passwordHash(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.mustChangePassword(), user.availableBudget(), enabled,
                enabled ? null : now,
                enabled ? null : currentAdminId(),
                user.createdAt()));
        return toResponse(updated);
    }

    public ResetPasswordResponse resetPassword(Long id) {
        User user = get(id);
        String temporaryPassword = randomPassword();
        userRepository.update(new User(
                user.id(), user.nickname(), user.email(), passwordEncoder.encode(temporaryPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                true, user.availableBudget(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        return new ResetPasswordResponse(temporaryPassword);
    }

    private User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다"));
    }

    private long countAdmins() {
        return userRepository.findAll().stream().filter(u -> u.role() == UserRole.ADMIN).count();
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
