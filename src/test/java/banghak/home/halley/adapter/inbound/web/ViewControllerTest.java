package banghak.home.halley.adapter.inbound.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 주소가 <b>서버로 오지 않는다</b> (설계 I244).
     *
     * <p>화면·모달 주소를 {@code #} 뒤로 옮겼습니다. 브라우저는 {@code #} 뒤를 서버에
     * 보내지 않으므로, 어떤 화면을 열든 <b>요청은 `/` 하나</b>입니다.
     *
     * <p>전에는 여기서 주소 스물여섯 개가 셸을 돌려주는지 봤습니다. 그 목록은
     * {@code app.js} 의 {@code ROUTES} 와 <b>같은 것을 두 번 적은 것</b>이었고,
     * 이제 한 곳에만 있습니다.
     *
     * <p><b>옛 링크는 그대로 열립니다.</b> {@code SpaRoutingFilter} 가 API·정적파일이
     * 아닌 GET 을 전부 셸로 넘기므로, 이미 나간 {@code /properties/12/score} 도
     * 200 을 받습니다 — 다만 {@code #} 가 비어 있어 <b>목록이 열립니다.</b>
     */
    @ParameterizedTest
    @ValueSource(strings = { "/properties/12/score", "/list", "/tour-plan" })
    @DisplayName("옛 주소로 들어와도 셸을 돌려준다 — 열리는 화면은 다르다 (설계 I244)")
    void staleDeepLinksStillReturnTheShell(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/"));
    }

    @Test
    @DisplayName("API 와 정적 파일은 셸로 넘기지 않는다 — 넘기면 404 가 200 이 된다")
    void apiAndStaticAreNotForwarded() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(result -> {
                    if ("/".equals(result.getResponse().getForwardedUrl())) {
                        throw new AssertionError("API 요청이 셸로 넘어갔다");
                    }
                });
        mockMvc.perform(get("/js/nope.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("루트 경로는 index 셸을 반환하고 카카오 JS 키를 모델에 담는다")
    void shellReturnsIndexWithKakaoJsKey() throws Exception {
        // when
        mockMvc.perform(get("/"))

                // then
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("kakaoJsKey"));
    }
}
