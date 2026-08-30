package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.ProfileRequest;
import banghak.home.halley.config.exception.AuthenticationRequiredException;
import banghak.home.halley.config.exception.DuplicateLoginIdException;
import banghak.home.halley.config.exception.DuplicateNicknameException;
import banghak.home.halley.config.exception.LastAdminException;
import banghak.home.halley.config.exception.NotFoundUserException;
import banghak.home.halley.config.exception.SelfDeleteException;
import banghak.home.halley.config.exception.SelfDisableException;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.event.WorkplacesChangedEvent;
import banghak.home.halley.config.HalleyUserDetails;
import org.springframework.context.ApplicationEventPublisher;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.domain.group.GroupNameGenerator;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.config.exception.GroupNotFoundException;
import banghak.home.halley.adapter.inbound.web.dto.NicknameCheckResponse;
import banghak.home.halley.adapter.inbound.web.dto.SignUpRequest;
import banghak.home.halley.adapter.inbound.web.dto.WithdrawRequest;
import banghak.home.halley.config.exception.AdminCannotWithdrawException;
import banghak.home.halley.config.exception.InvalidPasswordException;
import org.springframework.transaction.annotation.Transactional;
import banghak.home.halley.adapter.inbound.web.dto.UserDebtRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserDebtResponse;
import banghak.home.halley.adapter.outbound.persistence.UserDebtRepository;
import banghak.home.halley.domain.loan.ExistingDebt;
import banghak.home.halley.domain.loan.RegulationParams;
import banghak.home.halley.config.exception.SignUpClosedException;
import org.springframework.beans.factory.annotation.Value;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class UserService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupService groupService;
    private final NicknameSnapshotWriter nicknameSnapshotWriter;
    private final UserDebtRepository userDebtRepository;
    /** 회원가입 개방 여부 (설계 I95). */
    private final boolean signUpOpen;
    private final PasswordEncoder passwordEncoder;
    private final ScoringService scoringService;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, UserGroupRepository userGroupRepository,
                       GroupService groupService, NicknameSnapshotWriter nicknameSnapshotWriter,
                       UserDebtRepository userDebtRepository,
                       @Value("${membership.sign-up.open:true}") boolean signUpOpen,
                       PasswordEncoder passwordEncoder,
                       ScoringService scoringService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
        this.groupService = groupService;
        this.nicknameSnapshotWriter = nicknameSnapshotWriter;
        this.userDebtRepository = userDebtRepository;
        this.signUpOpen = signUpOpen;
        this.passwordEncoder = passwordEncoder;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
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
        final long newBudget = request.availableBudget() != null
                ? request.availableBudget() : user.availableBudget();

        final User updated = userRepository.update(new User(
                user.id(), user.loginId(), nickname,null, user.passwordHash(), user.role(),
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
        // 직장 위치는 AI 추천도의 입력이다 (설계 I60)
        if (workplaceChanged(user, updated)) {
            eventPublisher.publishEvent(new WorkplacesChangedEvent("profile:" + updated.id()));
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


    /**
     * 회원이 속할 그룹을 정한다 (설계 I87).
     *
     * <p><b>회원은 반드시 어느 그룹엔가 속합니다.</b> 그룹 없는 회원은 매물을 등록할 수도,
     * 볼 수도 없어 아무것도 못 하는 상태가 됩니다. admin이 지정하지 않았으면 새 그룹을
     * 만들어 넣습니다 — 이름은 무작위 한국어이고 나중에 그룹의 누구나 바꿉니다(규칙 14).
     *
     * <p>admin은 어느 그룹에도 속하지 않습니다(규칙 5).
     */
    private Long resolveGroupId(UserRole role, Long requested) {
        if (role == UserRole.ADMIN) {
            return null;
        }
        if (requested != null) {
            return userGroupRepository.findById(requested)
                    .map(UserGroup::id)
                    .orElseThrow(GroupNotFoundException::new);
        }
        return userGroupRepository.save(
                new UserGroup(null, GroupNameGenerator.generate(), null, null, Instant.now())).id();
    }


    /**
     * 스스로 하는 회원가입 (설계 I89 · 규칙 13·14).
     *
     * <p><b>새 그룹이 함께 만들어집니다.</b> 그룹 없는 회원은 매물을 등록할 수도 볼 수도 없어
     * 아무것도 못 하는 상태가 됩니다. 이름은 무작위 한국어이고 나중에 누구나 바꿉니다.
     *
     * <p>가입은 <b>회원(MEMBER)만</b> 됩니다 — 관리자를 스스로 만들 수 있으면 안 됩니다.
     */
    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        // 화면에서 링크를 숨기는 것만으로는 부족하다 — 주소를 아는 사람은 그냥 부른다 (설계 I95)
        if (!signUpOpen) {
            throw new SignUpClosedException();
        }
        return create(new CreateUserRequest(
                request.loginId(), request.nickname(), null, request.password(),
                UserRole.MEMBER, null, null, null, 0L, 0L, 0L));
    }

    /** 닉네임을 쓸 수 있는지 (규칙 17). 자기 닉네임은 그대로 둘 수 있어야 한다. */
    public NicknameCheckResponse checkNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return new NicknameCheckResponse(nickname, false);
        }
        final String trimmed = nickname.trim();
        final Long myId = currentUserId();
        final boolean taken = userRepository.findByNickname(trimmed)
                .filter(u -> myId == null || !u.id().equals(myId))
                .isPresent();
        return new NicknameCheckResponse(trimmed, !taken);
    }

    /**
     * 회원 탈퇴 (규칙 15·16).
     *
     * <p><b>닉네임을 빼고 모두 지웁니다.</b> 다만 이 회원이 남긴 매물·코멘트·쾌적함 점수는
     * 그룹이 살아 있는 한 그대로 둡니다 — 함께 보던 사람에게는 여전히 필요한 자료입니다.
     * 화면에 이름이 남아야 하므로 <b>닉네임은 매물·코멘트에 값으로 복사해</b> 둡니다(I88).
     *
     * <p>마지막 한 사람이 나가면 그룹과 매물이 함께 사라집니다(규칙 4).
     */
    @Transactional
    public void withdraw(WithdrawRequest request) {
        final User me = get(currentUserId());
        if (request == null || request.password() == null
                || !passwordEncoder.matches(request.password(), me.passwordHash())) {
            throw new InvalidPasswordException();
        }
        if (me.role() == UserRole.ADMIN) {
            throw new AdminCannotWithdrawException();
        }
        nicknameSnapshotWriter.snapshot(me.id(), me.nickname());
        final Long groupId = me.groupId();
        // 부채는 회원 정보다. 매물·코멘트와 달리 남길 이유가 없다 (규칙 16)
        userDebtRepository.deleteByUserId(me.id());
        userRepository.delete(me.id());
        groupService.deleteIfEmpty(groupId);
        log.info("User withdrew. userId={}, groupId={}", me.id(), groupId);
    }


    /**
     * 종류별 기존 부채 (설계 I92 · 로드맵 5단계).
     *
     * <p>연간 상환액을 함께 돌려줍니다 — 같은 1억이라도 신용대출이면 주담대의 서너 배로
     * 잡히는데, 숫자만 보면 그 이유를 알 수 없습니다.
     */
    public List<UserDebtResponse> myDebts() {
        final double rate = defaultAnnualRate();
        return userDebtRepository.findByUserId(currentUserId()).stream()
                .map(debt -> UserDebtResponse.from(debt, rate))
                .toList();
    }

    @Transactional
    public List<UserDebtResponse> replaceMyDebts(List<UserDebtRequest> requests) {
        final Long me = currentUserId();
        userDebtRepository.replaceAll(me, requests == null ? List.of() : requests.stream()
                .filter(r -> r.type() != null && r.amount() != null)
                .map(r -> new ExistingDebt(r.type(), r.amount()))
                .toList());
        return myDebts();
    }

    /** 부담을 보여 주기 위한 기준 금리. 실제 계산은 대출 산정이 시장 금리로 다시 한다(I81). */
    private double defaultAnnualRate() {
        return RegulationParams.defaults().interestRate().doubleValue()
                + RegulationParams.defaults().stressRate().doubleValue();
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }
        if (userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User saved = userRepository.save(new User(
                null,
                request.loginId(),
                request.nickname(),
                resolveGroupId(request.role() == null ? UserRole.MEMBER : request.role(),
                        request.groupId()),
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
        eventPublisher.publishEvent(new WorkplacesChangedEvent("user-created:" + saved.id()));
        return toResponse(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        final User user = get(id);
        if (!user.loginId().equals(request.loginId())
                && userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }
        if (!user.nickname().equals(request.nickname()) && userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new DuplicateNicknameException();
        }
        final User updated = userRepository.update(new User(
                user.id(),
                request.loginId(),
                request.nickname(), user.groupId(),
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
        if (workplaceChanged(user, updated)) {
            eventPublisher.publishEvent(new WorkplacesChangedEvent("user-updated:" + updated.id()));
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
                user.id(), user.loginId(), user.nickname(), user.groupId(), user.passwordHash(), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                user.mustChangePassword(), user.availableBudget(),
                user.annualIncomeOrZero(), user.existingLoanOrZero(), enabled,
                enabled ? null : now,
                enabled ? null : currentAdminId(),
                user.createdAt()));
        if (enabled != user.enabled()) {
            scoringService.rescoreAll();
            eventPublisher.publishEvent(new WorkplacesChangedEvent("user-enabled:" + updated.id()));
        }
        return toResponse(updated);
    }

    public ResetPasswordResponse resetPassword(Long id) {
        final User user = get(id);
        final String temporaryPassword = randomPassword();
        userRepository.update(new User(
                user.id(), user.loginId(), user.nickname(), user.groupId(),
                passwordEncoder.encode(temporaryPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                true, user.availableBudget(),
                user.annualIncomeOrZero(), user.existingLoanOrZero(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));
        return new ResetPasswordResponse(temporaryPassword);
    }

    /** 직장 위치(이름·좌표)가 실제로 달라졌는지. 예산만 고친 경우까지 LLM을 부르지 않는다. */
    private boolean workplaceChanged(User before, User after) {
        return !Objects.equals(before.workplaceName(), after.workplaceName())
                || compare(before.workplaceLat(), after.workplaceLat()) != 0
                || compare(before.workplaceLng(), after.workplaceLng()) != 0;
    }

    /** BigDecimal은 scale이 달라도 같은 값일 수 있어 equals 대신 compareTo로 본다. */
    private int compare(BigDecimal before, BigDecimal after) {
        if (before == null && after == null) {
            return 0;
        }
        if (before == null || after == null) {
            return 1;
        }
        return before.compareTo(after);
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
                user.id(), user.loginId(), user.nickname(), user.role(),
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
