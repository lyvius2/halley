package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.outbound.persistence.LlmRecommendationRepository;
import banghak.home.halley.adapter.outbound.persistence.UserCriterionScoreRepository;
import banghak.home.halley.application.service.PropertyEnrichmentService;
import banghak.home.halley.application.service.UserService;
import banghak.home.halley.domain.llm.LlmRecommendation;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매물 하위 API 는 전부 그룹 길목을 지난다 (설계 I294).
 *
 * <p>{@code PropertyAccessGuard} 는 자신을 "단 하나의 길목"이라 선언했는데, 여섯 경로가
 * 지나지 않고 있었습니다 — 남의 매물 점수를 <b>고칠 수도</b> 있었습니다. 표로 두어
 * 경로가 늘어도 한 줄만 더하면 되게 합니다.
 *
 * <p><b>404 인지까지 봅니다.</b> 403 은 "그 번호의 매물이 존재한다"는 사실을 알려 줍니다(설계 I87).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("매물 하위 API 의 그룹 경계 (설계 I294)")
class GroupBoundaryApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 접근 제어만 본다 — 보정이 같은 매물에 트랜잭션을 잡으면 엉뚱하게 느려진다 */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserCriterionScoreRepository userCriterionScoreRepository;
    @Autowired private LlmRecommendationRepository llmRecommendationRepository;

    private MockHttpSession owner;
    private MockHttpSession stranger;
    private String propertyId;

    @BeforeEach
    void twoGroupsOneProperty() throws Exception {
        // given — 서로 다른 그룹의 두 사람. groupId 를 비우면 각자 새 그룹이 생긴다
        final int n = SEQ.incrementAndGet();
        owner = login("owner" + n);
        stranger = login("stranger" + n);
        final String created = mockMvc.perform(post("/api/properties").session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"경계 매물\",\"dealType\":\"SALE\",\"priceDeposit\":300000000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        propertyId = objectMapper.readTree(created).get("property").get("id").asString();
        // 추천도를 하나 심어 둔다. 비어 있으면 find() 의 길목이 빠져도 orElseGet 의
        // isRunning() 길목이 대신 막아 주어, 새는 것을 시험이 못 본다
        llmRecommendationRepository.upsert(new LlmRecommendation(
                null, Long.valueOf(propertyId), new BigDecimal("77"), "시험용", "m", "h", 1, Instant.now()));
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = { "/land-use", "/llm-recommendation" })
    @DisplayName("다른 그룹의 매물을 읽으면 404 — 있는지조차 알려 주지 않는다")
    void strangerCannotRead(String path) throws Exception {
        // when / then
        mockMvc.perform(get("/api/properties/" + propertyId + path).session(stranger))
                .andExpect(status().isNotFound());
        // 주인은 된다 — 길목이 너무 세게 막힌 것이 아닌지
        mockMvc.perform(get("/api/properties/" + propertyId + path).session(owner))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = { "/land-use", "/rescore", "/scores/recompute" })
    @DisplayName("다른 그룹의 매물을 다시 계산시키면 404")
    void strangerCannotTrigger(String path) throws Exception {
        // when / then
        mockMvc.perform(post("/api/properties/" + propertyId + path).session(stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/properties/" + propertyId + path).session(owner))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "PUT /scores")
    @ValueSource(strings = { "/scores" })
    @DisplayName("다른 그룹의 매물 점수를 고치면 404 — DB 도 그대로다")
    void strangerCannotWriteScores(String path) throws Exception {
        // when
        mockMvc.perform(put("/api/properties/" + propertyId + path).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":{\"COMFORT\":5}}"))
                .andExpect(status().isNotFound());

        // then — 남의 손이 닿은 흔적이 없어야 한다
        assertThat(userCriterionScoreRepository.findByPropertyId(Long.valueOf(propertyId)))
                .as("404 를 돌려주고도 점수가 저장됐다")
                .isEmpty();

        // 주인은 된다
        mockMvc.perform(put("/api/properties/" + propertyId + path).session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":{\"COMFORT\":5}}"))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String loginId) throws Exception {
        userService.create(new CreateUserRequest(
                loginId, loginId, null, "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L));
        final MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1!\",\"newPassword\":\"newpassword2!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/users/me/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workplaceName":"회사","workplaceLat":37.5,"workplaceLng":127.0,
                                 "availableBudget":300000000,"annualIncome":60000000,"existingLoan":0}
                                """))
                .andExpect(status().isOk());
        return session;
    }
}
