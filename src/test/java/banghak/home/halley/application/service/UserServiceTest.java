package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.WorkplaceRequest;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.DuplicateEmailException;
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
                "member1", "member1@example.com", "pw12345!", UserRole.MEMBER, "회사", null, null, 500_000_000L);

        // when
        final UserResponse created = userService.create(request);

        // then
        assertThat(created.id()).isNotNull();
        assertThat(created.role()).isEqualTo(UserRole.MEMBER);
        assertThat(created.availableBudget()).isEqualTo(500_000_000L);
        assertThat(userService.list()).extracting(UserResponse::email).contains("member1@example.com");
    }

    @Test
    @DisplayName("중복 이메일로 생성하면 DuplicateEmailException이 발생한다")
    void createDuplicateEmailFails() {
        // given
        userService.create(new CreateUserRequest("dup1", "dup@example.com", "pw12345!", null, null, null, null, null));

        // when
        final DuplicateEmailException ex = assertThrows(
                DuplicateEmailException.class,
                () -> userService.create(new CreateUserRequest("dup2", "dup@example.com", "pw12345!", null, null, null, null, null)));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("EMAIL_DUPLICATED");
    }

    @Test
    @DisplayName("사용자를 수정하면 닉네임·이메일·예산이 갱신된다")
    void update() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "update-user", "update@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        // when
        final UserResponse updated = userService.update(created.id(), new UpdateUserRequest(
                "update-user2", "update2@example.com", "새회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 100_000_000L));

        // then
        assertThat(updated.nickname()).isEqualTo("update-user2");
        assertThat(updated.email()).isEqualTo("update2@example.com");
        assertThat(updated.availableBudget()).isEqualTo(100_000_000L);
    }

    @Test
    @DisplayName("비밀번호를 리셋하면 12자 임시 비밀번호가 반환되고 재변경 플래그가 켜진다")
    void resetPassword() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "reset-user", "reset@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        // when
        final ResetPasswordResponse reset = userService.resetPassword(created.id());

        // then
        assertThat(reset.temporaryPassword()).hasSize(12);
        assertThat(userRepository.findByEmail("reset@example.com").get().mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("내 프로필을 조회하고 직장 위치를 수정한다")
    void meAndWorkplace() {
        // given
        userService.create(new CreateUserRequest(
                "프로필", "me@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));
        final User user = userRepository.findByEmail("me@example.com").orElseThrow();
        final HalleyUserDetails details = new HalleyUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));

        // when
        final UserResponse me = userService.me();
        final UserResponse updated = userService.updateWorkplace(
                new WorkplaceRequest("회사", new BigDecimal("37.5"), new BigDecimal("126.9")));

        // then
        assertThat(me.email()).isEqualTo("me@example.com");
        assertThat(updated.workplaceName()).isEqualTo("회사");
        assertThat(updated.workplaceLat()).isEqualByComparingTo("37.5");

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("사용자를 삭제하면 저장소에서 제거된다")
    void delete() {
        // given
        final UserResponse created = userService.create(new CreateUserRequest(
                "delete-user", "delete@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        // when
        userService.delete(created.id());

        // then
        assertThat(userRepository.findById(created.id())).isEmpty();
    }
}
