package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.ApiException;
import banghak.home.halley.adapter.inbound.web.dto.AuthResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.port.out.persistence.UserRepository;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
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
    void bootstrapAdminExists() {
        assertThat(userRepository.findByEmail("admin@halley.local")).isPresent();
        assertThat(userRepository.findByEmail("admin@halley.local").get().role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void loginThenChangePassword() {
        userService.create(new CreateUserRequest("auth-user", "auth@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));

        AuthResponse first = authService.login("auth@example.com", "password1!");
        assertThat(first.mustChangePassword()).isTrue();
        assertThat(first.role()).isEqualTo(UserRole.MEMBER);

        authService.changePassword("password1!", "newpassword2!");
        assertThat(userRepository.findByEmail("auth@example.com").get().mustChangePassword()).isFalse();

        SecurityContextHolder.clearContext();
        AuthResponse second = authService.login("auth@example.com", "newpassword2!");
        assertThat(second.mustChangePassword()).isFalse();
    }

    @Test
    void loginWithWrongPasswordFails() {
        userService.create(new CreateUserRequest("wrong-user", "wrong@example.com", "password1!", UserRole.MEMBER, null, null, null, 0L));

        ApiException ex = catchThrowableOfType(
                () -> authService.login("wrong@example.com", "bad-password"),
                ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
    }
}
