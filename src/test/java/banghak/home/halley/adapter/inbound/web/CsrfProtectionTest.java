package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.support.CsrfMockMvcCustomizer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 쿠키 인증에 CSRF 방어가 걸려 있는가 (설계 I295).
 *
 * <p>쿠키는 브라우저가 알아서 붙이므로, 다른 사이트의 문서가 우리 API 로 POST 를 던지면
 * <b>로그인한 사람의 권한으로</b> 실행됐습니다. 토큰이 있어야 막힙니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("CSRF 방어 (설계 I295)")
class CsrfProtectionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy securityFilters;

    @Test
    @DisplayName("헤더가 쿠키와 다르면 403 — 인증보다 먼저 막힌다")
    void rejectsAMismatchedToken() throws Exception {
        // when / then — 다른 출처의 문서는 쿠키를 못 읽으니 맞는 헤더를 만들 수 없다
        mockMvc.perform(post("/api/auth/login")
                        .header("X-XSRF-TOKEN", "forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("헤더가 없어도 403")
    void rejectsAMissingToken() throws Exception {
        // given — 기본 요청의 헤더를 지운다 (쿠키만 있는, 다른 사이트가 던진 모양)
        final MockMvc bare = bareMockMvc();

        // when / then
        bare.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CsrfMockMvcCustomizer.TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("헤더가 쿠키와 같으면 CSRF 로는 막히지 않는다")
    void allowsAMatchingToken() throws Exception {
        // when / then — 403 이 아니어야 한다. 401 은 자격이 틀린 것이지 CSRF 가 아니다
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("셸을 받으면 토큰 쿠키가 함께 온다 — 첫 POST 부터 헤더를 만들 수 있다")
    void shellIssuesTheTokenCookie() throws Exception {
        // given — 기본 요청에 쿠키가 실려 있으면 새로 발급할 일이 없다. 맨 요청으로 본다
        final MockMvc bare = bareMockMvc();

        // when / then — 브라우저가 받는 것은 Set-Cookie 헤더다
        bare.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
    }

    /** 시험 지원의 기본 쿠키·헤더 없이, 실제 보안 필터만 건 MockMvc. */
    private MockMvc bareMockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
    }
}
