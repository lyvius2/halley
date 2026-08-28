package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.ProfileRequest;
import banghak.home.halley.config.exception.AuthenticationRequiredException;
import banghak.home.halley.config.exception.DuplicateEmailException;
import banghak.home.halley.config.exception.DuplicateLoginIdException;
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
    private final ScoringService scoringService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ScoringService scoringService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.scoringService = scoringService;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse me() {
        return toResponse(get(currentUserId()));
    }

    public UserResponse updateProfile(ProfileRequest request) {
        final User user = get(currentUserId());
        final String nickname = request.nickname() == null || request.nickname().isBlank()
                ? user.nickname() : request.nickname().trim();
        if (!user.nickname().equals(nickname) && userRepository.findByNickname(nickname).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final String email = hasText(request.email()) ? request.email().trim() : user.email();
        if (hasText(email) && !email.equals(user.email())
                && userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }
        final long newBudget = request.availableBudget() != null
                ? request.availableBudget() : user.availableBudget();

        final User updated = userRepository.update(new User(
                user.id(), user.loginId(), nickname, email, user.passwordHash(), user.role(),
                request.workplaceName(), request.workplaceLat(), request.workplaceLng(),
                user.mustChangePassword(), newBudget,
                request.annualIncome() != null ? request.annualIncome() : user.annualIncomeOrZero(),
                request.existingLoan() != null ? request.existingLoan() : user.existingLoanOrZero(),
                user.enabled(), user.disabledAt(), user.disabledBy(), user.createdAt()));
        refreshProfileFlag(updated);

        // 예산 상한이 바뀌면 전 매물 PRICE가 달라진다 (설계 5.2.1)
        if (newBudget != user.availableBudget()) {
            scoringService.rescoreAll();
        }
        return toResponse(updated);
    }

    /** 프로필을 채우면 AccountSetupFilter가 더 이상 막지 않도록 세션의 principal을 갱신한다. */
    private void refreshProfileFlag(User updated) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            principal.setProfileComplete(updated.profileComplete());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Long currentUserId() {
        final Long id = currentAdminId();
        if (id == null) {
            throw new AuthenticationRequiredException();
        }
        return id;
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }
        if (hasText(request.email()) && userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }
        if (userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User saved = userRepository.save(new User(
                null,
                request.loginId(),
                request.nickname(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role() == null ? UserRole.MEMBER : request.role(),
                request.workplaceName(),
                request.workplaceLat(),
                request.workplaceLng(),
                true,
                request.availableBudget() == null ? 0L : request.availableBudget(),
                request.annualIncome() == null ? 0L : request.annualIncome(),
                request.existingLoan() == null ? 0L : request.existingLoan(),
                true,
                null, null, null));
        scoringService.rescoreAll();
        return toResponse(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        final User user = get(id);
        if (!user.loginId().equals(request.loginId())
                && userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }
        if (hasText(request.email()) && !request.email().equals(user.email())
                && userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }
        if (!user.nickname().equals(request.nickname()) && userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User updated = userRepository.update(new User(
                user.id(),
                request.loginId(),
                request.nickname(),
                request.email(),
                user.passwordHash(),
                user.role(),
                request.workplaceName(),
                request.workplaceLat(),
                request.workplaceLng(),
                user.mustChangePassword(),
                request.availableBudget() == null ? user.availableBudget() : request.availableBudget(),
                request.annualIncome() == null ? user.annualIncomeOrZero() : request.annualIncome(),
                request.existingLoan() == null ? user.existingLoanOrZero() : request.existingLoan(),
                user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        final long newBudget = request.availableBudget() == null ? user.availableBudget() : request.availableBudget();
        if (newBudget != user.availableBudget()) {
            scoringService.rescoreAll();
        }
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
                user.id(), user.loginId(), user.nickname(), user.email(), user.passwordHash(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.mustChangePassword(), user.availableBudget(),
                user.annualIncomeOrZero(), user.existingLoanOrZero(), enabled,
                enabled ? null : now,
                enabled ? null : currentAdminId(),
                user.createdAt()));
        if (enabled != user.enabled()) {
            scoringService.rescoreAll();
        }
        return toResponse(updated);
    }

    public ResetPasswordResponse resetPassword(Long id) {
        final User user = get(id);
        final String temporaryPassword = randomPassword();
        userRepository.update(new User(
                user.id(), user.loginId(), user.nickname(), user.email(),
                passwordEncoder.encode(temporaryPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                true, user.availableBudget(),
                user.annualIncomeOrZero(), user.existingLoanOrZero(), user.enabled(),
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
                user.id(), user.loginId(), user.nickname(), user.email(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.availableBudget(), user.annualIncomeOrZero(), user.existingLoanOrZero(),
                user.enabled(), user.mustChangePassword(), user.createdAt());
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
