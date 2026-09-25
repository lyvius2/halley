package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 무차별 대입을 막는가 (설계 I298).
 *
 * <p>공개 주소로 운영하는데 실패 횟수 제한이 없었다. 한 계정을 한 주소에서 다섯 번 틀리면
 * 15분 막는다 — <b>맞는 비밀번호를 넣어도</b> 막힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("로그인 시도 제한 (설계 I298)")
class LoginRateLimitTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;

    @Test
    @DisplayName("다섯 번 틀리면 여섯 번째는 맞아도 429")
    void locksAfterFiveFailures() throws Exception {
        // given
        final String id = user();

        // when — 다섯 번 틀린다
        for (int i = 0; i < 5; i++) {
            login(id, "wrong!!!").andExpect(status().isUnauthorized());
        }

        // then — 맞는 비밀번호도 막힌다
        login(id, "password1!").andExpect(status().isTooManyRequests());
        // 다른 계정은 영향이 없다
        login(user(), "password1!").andExpect(status().isOk());
    }

    @Test
    @DisplayName("성공하면 처음부터 센다")
    void successResetsTheCount() throws Exception {
        // given — 두 번 틀리고 한 번 맞는다
        final String id = user();
        login(id, "wrong!!!").andExpect(status().isUnauthorized());
        login(id, "wrong!!!").andExpect(status().isUnauthorized());
        login(id, "password1!").andExpect(status().isOk());

        // when — 다시 네 번 틀린다 (앞의 둘이 남아 있었다면 여섯이 되어 잠긴다)
        for (int i = 0; i < 4; i++) {
            login(id, "wrong!!!").andExpect(status().isUnauthorized());
        }

        // then — 아직 잠기지 않았다
        login(id, "password1!").andExpect(status().isOk());
    }

    private ResultActions login(String id, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"));
    }

    private String user() {
        final String id = "limit" + SEQ.incrementAndGet();
        userService.create(new CreateUserRequest(
                id, id, null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        return id;
    }
}
