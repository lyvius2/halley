package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.SignUpRequest;
import banghak.home.halley.config.exception.SignUpClosedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원가입 개방 스위치 (설계 I95).
 *
 * <p>화면에서 링크를 숨기는 것만으로는 부족합니다 — <b>주소를 아는 사람은 그냥 부릅니다.</b>
 * 막는 일은 서버가 해야 하고, 그걸 여기서 확인합니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "membership.sign-up.open=false")
@DisplayName("회원가입이 닫혀 있을 때 (설계 I95)")
class SignUpGateTest {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("서버가 막는다 — 화면에서 숨기는 것만으로는 부족하다")
    void signUpIsRejectedWhenClosed() {
        assertThatThrownBy(() -> userService.signUp(
                new SignUpRequest("sneaky", "몰래", "password1!")))
                .isInstanceOf(SignUpClosedException.class);
    }

    @Test
    @DisplayName("admin의 회원 생성은 막지 않는다 — 닫는 것은 '스스로 가입'뿐이다")
    void adminCanStillCreateUsers() {
        assertThat(userService.list()).isNotNull();
    }
}
