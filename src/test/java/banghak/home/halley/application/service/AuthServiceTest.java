package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.AuthResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.exception.InvalidCredentialsException;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("local")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("최초 부팅 시 관리자 계정이 존재한다")
    void bootstrapAdminExists() {
        // then
        assertThat(userRepository.findByEmail("admin")).isPresent();
        assertThat(userRepository.findByEmail("admin").get().role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("로그인 후 비밀번호를 변경하면 재변경 플래그가 해제된다")
    void loginThenChangePassword() {
        // given
        userService.create(new CreateUserRequest("auth-user", "auth@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));

        // when
        final AuthResponse first = authService.login("auth@example.com", "password1!");

        // then
        assertThat(first.mustChangePassword()).isTrue();
        assertThat(first.role()).isEqualTo(UserRole.MEMBER);

        authService.changePassword("password1!", "newpassword2!");
        assertThat(userRepository.findByEmail("auth@example.com").get().mustChangePassword()).isFalse();

        SecurityContextHolder.clearContext();
        final AuthResponse second = authService.login("auth@example.com", "newpassword2!");
        assertThat(second.mustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인하면 InvalidCredentialsException이 발생한다")
    void loginWithWrongPasswordFails() {
        // given
        userService.create(new CreateUserRequest("wrong-user", "wrong@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));

        // when
        final InvalidCredentialsException ex = catchThrowableOfType(
                () -> authService.login("wrong@example.com", "bad-password"),
                InvalidCredentialsException.class);

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
    }
}
