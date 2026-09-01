package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slack 메시지에 <b>실제 내용</b>이 실린다 (설계 I201).
 *
 * <p>전에는 "의견을 남겼습니다"·"쾌적함을 평가했습니다"까지만 갔습니다.
 * 무슨 의견인지, 몇 점인지 보려면 <b>들어가야</b> 했는데 대개 그 한 줄이 알림의 전부입니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SlackMessageContentTest.StubConfig.class)
@TestPropertySource(properties = "app.base-url=https://halley.example.com")
@DisplayName("Slack 메시지 내용 (설계 I201)")
class SlackMessageContentTest {

    private static final String WEBHOOK = "https://hooks.slack.com/services/T/B/content";

    @TestConfiguration
    static class StubConfig {

        final List<String> messages = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        SlackPort slackPort() {
            return (webhookUrl, text) -> {
                messages.add(text);
                return true;
            };
        }
    }

    /** 이 테스트는 알림만 본다 — LLM 재질의가 끼면 엉뚱한 곳에서 느려진다. */
    @MockitoBean
    private LlmRecommendationService llmRecommendationService;

    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private StubConfig stub;
    @Autowired private SlackProperties slackProperties;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PropertyService propertyService;

    private Long propertyId;

    @BeforeEach
    void setUp() {
        stub.messages.clear();
        slackProperties.setEnabled(true);
        final Long groupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        userGroupRepository.updateWebhook(groupId, WEBHOOK);
propertyId = propertyService.create(new PropertyRequest(
                "알림매물", null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
    }

    @AfterEach
    void clearAuth() {
        slackProperties.setEnabled(false);
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("코멘트 알림에 그 글이 그대로 실린다")
    void commentCarriesItsText() {
        notificationService.sendCommentCreated(propertyId, "홍길동", "채광이 아주 좋았습니다");

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("홍길동").contains("알림매물").contains("의견을 남겼습니다");
            assertThat(text).contains("> 채광이 아주 좋았습니다");
            assertThat(text).endsWith("https://halley.example.com/properties/" + propertyId + "/comments");
        });
    }

    @Test
    @DisplayName("여러 줄 코멘트는 줄마다 인용한다 — 첫 줄만 하면 누가 쓴 글인지 흐려진다")
    void multiLineCommentIsQuotedPerLine() {
        notificationService.sendCommentCreated(propertyId, "홍길동", "1층이라 조용합니다\n다만 주차가 좁습니다");

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("> 1층이라 조용합니다");
            assertThat(text).contains("> 다만 주차가 좁습니다");
        });
    }

    @Test
    @DisplayName("긴 코멘트는 자른다 — 채널이 덮인다")
    void longCommentIsTruncated() {
        notificationService.sendCommentCreated(propertyId, "홍길동", "가".repeat(1000));

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("…");
            assertThat(text.length()).isLessThan(600);
        });
    }

    @Test
    @DisplayName("지운 코멘트에는 실을 내용이 없다 — 남겼다고 하지 않는다")
    void deletedCommentSaysSo() {
        notificationService.sendCommentCreated(propertyId, "홍길동", null);

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("의견을 지웠습니다");
            assertThat(text).doesNotContain("남겼습니다");
        });
    }

    @Test
    @DisplayName("쾌적함 알림에 실제 점수가 실린다 — 없으면 좋다는 건지 나쁘다는 건지 모른다")
    void comfortCarriesTheScore() {
        notificationService.sendComfortScored(propertyId, "홍길동", 4);

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("공간 쾌적함을 4점으로 평가했습니다 (5점 만점)");
            assertThat(text).endsWith("https://halley.example.com/properties/" + propertyId + "/score");
        });
    }

    @Test
    @DisplayName("점수를 모르면 지어내지 않는다")
    void unknownComfortScoreStaysUnsaid() {
        notificationService.sendComfortScored(propertyId, "홍길동", null);

        assertThat(stub.messages).singleElement().satisfies(text ->
                assertThat(text).contains("공간 쾌적함을 평가했습니다").doesNotContain("점으로"));
    }

    /**
     * Slack 은 `<...>` 를 태그로 읽는다 (설계 I201).
     *
     * <p>우리가 `<!channel>` 을 쓰므로, 사람이 쓴 글의 꺾쇠도 그대로 두면
     * <b>엉뚱한 태그</b>가 됩니다.
     */
    @Test
    @DisplayName("코멘트 속 꺾쇠는 태그가 되지 않는다")
    void angleBracketsAreEscaped() {
        notificationService.sendCommentCreated(propertyId, "홍길동", "<!here> 좀 보세요");

        assertThat(stub.messages).singleElement().satisfies(text -> {
            assertThat(text).contains("&lt;!here&gt;");
            // 우리가 붙인 것 하나만 남아야 한다
            assertThat(text.split("<!channel>", -1).length - 1).isEqualTo(1);
        });
    }
}
