package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.ApiException;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("local")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createAndList() {
        UserResponse created = userService.create(new CreateUserRequest(
                "member1", "member1@example.com", "pw12345!", UserRole.MEMBER, "회사", null, null, 500_000_000L));

        assertThat(created.id()).isNotNull();
        assertThat(created.role()).isEqualTo(UserRole.MEMBER);
        assertThat(created.availableBudget()).isEqualTo(500_000_000L);

        assertThat(userService.list()).extracting(UserResponse::email).contains("member1@example.com");
    }

    @Test
    void createDuplicateEmailFails() {
        userService.create(new CreateUserRequest("dup1", "dup@example.com", "pw12345!", null, null, null, null, null));

        ApiException ex = catchThrowableOfType(
                () -> userService.create(new CreateUserRequest("dup2", "dup@example.com", "pw12345!", null, null, null, null, null)),
                ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("EMAIL_DUPLICATED");
    }

    @Test
    void update() {
        UserResponse created = userService.create(new CreateUserRequest(
                "update-user", "update@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        UserResponse updated = userService.update(created.id(), new UpdateUserRequest(
                "update-user2", "update2@example.com", "새회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 100_000_000L));

        assertThat(updated.nickname()).isEqualTo("update-user2");
        assertThat(updated.email()).isEqualTo("update2@example.com");
        assertThat(updated.availableBudget()).isEqualTo(100_000_000L);
    }

    @Test
    void resetPassword() {
        UserResponse created = userService.create(new CreateUserRequest(
                "reset-user", "reset@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        ResetPasswordResponse reset = userService.resetPassword(created.id());

        assertThat(reset.temporaryPassword()).hasSize(12);
        assertThat(userRepository.findByEmail("reset@example.com").get().mustChangePassword()).isTrue();
    }

    @Test
    void delete() {
        UserResponse created = userService.create(new CreateUserRequest(
                "delete-user", "delete@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));

        userService.delete(created.id());

        assertThat(userRepository.findById(created.id())).isEmpty();
    }
}
