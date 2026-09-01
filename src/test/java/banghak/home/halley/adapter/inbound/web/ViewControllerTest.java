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
     * 모달 주소로 들어와도 앱이 뜬다 (설계 I198).
     *
     * <p><b>`ViewController` 의 매핑 목록이 이 일을 하는 것이 아닙니다.</b>
     * `SpaRoutingFilter` 가 API·정적파일이 아닌 GET 을 전부 `/` 로 넘깁니다 —
     * 매핑 목록은 <b>어떤 주소가 있는지 적어 둔 것</b>이지 동작의 근거가 아닙니다.
     * 그래서 여기서는 뷰 이름이 아니라 <b>200 이 오는지</b>를 봅니다. Slack 링크를
     * 눌렀을 때 화면이 뜨느냐가 실제 계약입니다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/properties", "/properties/new", "/properties/paste", "/properties/12",
            "/properties/12/score", "/properties/12/loan", "/properties/12/comments",
            "/properties/12/transactions", "/properties/12/edit", "/properties/12/forecast",
            "/properties/12/photos", "/properties/12/photos/3", "/properties/12/agents",
            "/properties/12/roadview", "/properties/12/paste",
            "/itinerary", "/me", "/group", "/weights",
            "/users", "/users/new", "/users/7/edit", "/compare", "/password", "/signup", "/settings"
    })
    @DisplayName("모달 주소로 직접 들어와도 셸을 돌려준다 (설계 I198)")
    void modalPathsReturnTheShell(String path) throws Exception {
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
