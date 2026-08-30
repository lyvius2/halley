package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.ProfileRequest;
import banghak.home.halley.adapter.inbound.web.dto.SignUpRequest;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.DuplicateLoginIdException;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자를 생성하면 id가 부여되고 목록에 조회된다")
    void createAndList() {
        // given
        final CreateUserRequest request = new CreateUserRequest(
                "member1", "member1", null, "pw12345!", UserRole.MEMBER, "회사", null, null, 500_000_000L, null, null);

        // when
        final UserResponse created = userService.create(request);

        // then
        assertThat(created.id()).isNotNull();
        assertThat(created.role()).isEqualTo(UserRole.MEMBER);
        assertThat(created.availableBudget()).isEqualTo(500_000_000L);
        assertThat(userService.list()).extracting(UserResponse::nickname).contains("member1");
    }

    @Test
    @DisplayName("중복 아이디로 생성하면 DuplicateLoginIdException이 발생한다")
    void createDuplicateLoginIdFails() {
        // given
        userService.create(new CreateUserRequest("same-id", "id-user1", null, "pw12345!", null, null, null, null, null, null, null));

        // when
        final DuplicateLoginIdException ex = assertThrows(
                DuplicateLoginIdException.class,
                () -> userService.create(new CreateUserRequest("same-id", "id-user2", null, "pw12345!", null, null, null, null, null, null, null)));

        // then
        assertThat(ex.getCode()).isEqualTo("LOGIN_ID_DUPLICATED");
    }

    @Test
    @DisplayName("사용자를 수정하면 닉네임·예산이 갱신된다")
    void update() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "update", "update-user", null, "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));

        // when
        final UserResponse updated = userService.update(created.id(), new UpdateUserRequest(
                "update2", "update-user2", "새회사",
                new BigDecimal("37.5"), new BigDecimal("127.0"), 100_000_000L, 60_000_000L, 0L));

        // then
        assertThat(updated.nickname()).isEqualTo("update-user2");
        assertThat(updated.availableBudget()).isEqualTo(100_000_000L);
    }

    @Test
    @DisplayName("관리자가 만든 계정은 첫 로그인에 비밀번호를 바꿔야 한다 — 남이 정한 것을 그대로 쓰면 안 된다")
    void adminCreatedUserMustChangePassword() {
        // when
        final UserResponse created = userService.create(new CreateUserRequest(
                "by-admin", "관리자가만듦", null, "password1!", UserRole.MEMBER,
                null, null, null, 0L, 0L, 0L));

        // then
        assertThat(created.mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("스스로 가입하면 방금 정한 비밀번호를 다시 묻지 않는다")
    void selfSignUpKeepsChosenPassword() {
        // when
        final UserResponse created = userService.signUp(
                new SignUpRequest("self-signed", "스스로가입", "password1!"));

        // then
        assertThat(created.mustChangePassword()).isFalse();
        // 다만 프로필은 아직 확인 전이다 — 값이 비어 있으므로 한 번은 보게 한다
        assertThat(userRepository.findByLoginId("self-signed").orElseThrow().profileConfirmed())
                .isFalse();
    }

    @Test
    @DisplayName("프로필을 저장하면 확인한 것으로 본다 — 채워진 것과 본인이 맞다고 한 것은 다르다")
    void savingProfileMarksItConfirmed() {
        // given
        final Long id = userService.create(new CreateUserRequest(
                "confirmer", "확인자", null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"),
                300_000_000L, 60_000_000L, 0L)).id();
        assertThat(userRepository.findById(id).orElseThrow().profileConfirmed()).isFalse();
        loginAs(id);

        // when
        userService.updateProfile(new ProfileRequest("확인자", "회사",
                new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));

        // then
        assertThat(userRepository.findById(id).orElseThrow().profileConfirmed()).isTrue();
    }

    @Test
    @DisplayName("프로필을 저장하면 세션에도 반영된다 — 안 그러면 확인 화면이 다시 뜬다")
    void savingProfileRefreshesSession() {
        // given
        final Long id = userService.create(new CreateUserRequest(
                "session-refresh", "세션갱신", null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"),
                300_000_000L, 60_000_000L, 0L)).id();
        loginAs(id);

        // when
        userService.updateProfile(new ProfileRequest("세션갱신", "회사",
                new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));

        // then — 세션 응답은 DB가 아니라 로그인할 때 담아 둔 principal에서 읽는다
        final var principal = (HalleyUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.isProfileConfirmed()).isTrue();
    }

    private void loginAs(Long userId) {
        final HalleyUserDetails details =
                new HalleyUserDetails(userRepository.findById(userId).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Test
    @DisplayName("비밀번호를 리셋하면 12자 임시 비밀번호가 반환되고 재변경 플래그가 켜진다")
    void resetPassword() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "reset", "reset-user", null, "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));

        // when
        final ResetPasswordResponse reset = userService.resetPassword(created.id());

        // then
        assertThat(reset.temporaryPassword()).hasSize(12);
        assertThat(userRepository.findByLoginId("reset").get().mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("내 프로필을 조회하고 직장 위치를 수정한다")
    void meAndWorkplace() {
        // given
        userService.create(new CreateUserRequest(
                "me", "프로필", null, "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));
        final User user = userRepository.findByLoginId("me").orElseThrow();
        final HalleyUserDetails details = new HalleyUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));

        // when
        final UserResponse me = userService.me();
        final UserResponse updated = userService.updateProfile(
                new ProfileRequest("바뀐닉", "회사",
                        new BigDecimal("37.5"), new BigDecimal("126.9"), 300_000_000L, 60_000_000L, 0L));

        // then
        assertThat(updated.workplaceName()).isEqualTo("회사");
        assertThat(updated.workplaceLat()).isEqualByComparingTo("37.5");
        assertThat(updated.availableBudget()).isEqualTo(300_000_000L);
        assertThat(updated.nickname()).isEqualTo("바뀐닉");

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("사용자를 삭제하면 저장소에서 제거된다")
    void delete() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "delete", "delete-user", null, "pw12345!", UserRole.MEMBER, null, null, null, 0L, 60_000_000L, 0L));

        // when
        userService.delete(created.id());

        // then
        assertThat(userRepository.findById(created.id())).isEmpty();
    }
}
