package banghak.home.halley.adapter.inbound.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입은 <b>아무것도 안 정하면 닫혀 있다</b> (설계 I296).
 *
 * <p>2인 전용 폐쇄형인데 기본값이 열림이었다. 환경변수를 빠뜨린 배포가 곧 가입이 열린
 * 배포였다. 이 시험은 아무 속성도 덮어쓰지 않는다 — 그래야 <b>기본값</b>을 재는 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("회원가입 기본값은 닫힘 (설계 I296)")
class SignUpDefaultClosedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공개 설정이 닫힘이라고 말한다")
    void publicConfigSaysClosed() throws Exception {
        // when / then
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signUpOpen").value(false));
    }

    @Test
    @DisplayName("로그인 전 닉네임 확인도 함께 닫힌다 — 계정 열거 창구가 된다 (설계 I300)")
    void nicknameCheckIsClosedToo() throws Exception {
        // when / then
        mockMvc.perform(get("/api/users/nickname-check").param("nickname", "admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("가입 요청은 서버가 막는다")
    void signUpIsRejected() throws Exception {
        // when / then
        mockMvc.perform(post("/api/users/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"walkin\",\"nickname\":\"뜨내기\",\"password\":\"password1!\"}"))
                .andExpect(status().is4xxClientError());
    }
}
