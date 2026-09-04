package banghak.home.halley.adapter.inbound.web;

import org.hamcrest.Matchers;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    /**
     * app.js·app.css 주소에 배포마다 바뀌는 값이 붙어 있는가 (설계 I277).
     *
     * <p>버전이 없으면 재배포해도 브라우저가 예전 파일을 계속 쓴다 — 서버는
     * 새 코드인데 화면은 옛 코드로 도는 채로 남는다.
     */
    @Test
    @DisplayName("정적 파일 주소에 캐시 무효화용 버전이 붙는다")
    void staticAssetsCarryACacheBustingVersion() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("assetVersion"))
                .andExpect(content().string(Matchers.containsString("/js/app.js?v=")))
                .andExpect(content().string(Matchers.containsString("/css/app.css?v=")));
    }

    /**
     * AI 모델 드롭다운이 저장된 값을 고른 채로 뜨는가 (설계 I280).
     *
     * <p><b>`x-model` 만으로는 안 됩니다.</b> `<option>` 은 `x-for` 로 나중에 붙는데,
     * 그전에 `x-model` 이 값을 넣으면 맞는 항목이 없어 브라우저가 첫 항목
     * ("기본 모델")을 고릅니다 — <b>저장은 됐는데 화면만 안 고른 것처럼</b> 보입니다.
     * 실제로 그렇게 신고가 들어왔고, `:selected` 를 함께 걸어 고쳤습니다.
     *
     * <p>화면 동작을 Java 로 재현할 수는 없으니, <b>고친 표시가 남아 있는지</b>만
     * 지킵니다. 지우면 같은 버그가 조용히 돌아옵니다.
     */
    @Test
    @DisplayName("AI 모델 드롭다운은 저장된 값을 :selected 로도 표시한다 (설계 I280)")
    void modelDropdownMarksTheSavedOptionSelected() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.containsString(":selected=\"m.id === llmForm[f.key]\"")));
    }
}
