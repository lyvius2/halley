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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 등록·삭제 알림이 <b>실제로 나가는지</b> (설계 I216).
 *
 * <p>이 테스트가 없어서 버그가 오래 살았습니다. 기존 알림 테스트는
 * `notificationService` 를 <b>직접</b> 불러 확인했는데, 정작 끊어져 있던 곳은
 * 그 앞 — <b>이벤트가 리스너까지 닿는 구간</b>이었습니다.
 *
 * <p>`PropertyService.create` 에 트랜잭션이 없는데 리스너가 `AFTER_COMMIT` 만
 * 기다리고 있어, 이벤트가 <b>예외도 로그도 없이 버려졌습니다.</b>
 * 서비스만 부르는 테스트로는 영원히 못 잡습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PropertyNotificationWiringTest.StubConfig.class)
@DisplayName("등록·삭제 알림 배선 (설계 I216)")
class PropertyNotificationWiringTest {

    private static final String WEBHOOK = "https://hooks.slack.com/services/T/B/wiring";

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

    /** 알림 배선만 본다 — 보정·LLM 이 끼면 엉뚱한 곳에서 느려진다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @MockitoBean
    private LlmRecommendationService llmRecommendationService;

    @Autowired private StubConfig stub;
    @Autowired private SlackProperties slackProperties;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        stub.messages.clear();
        slackProperties.setEnabled(true);
        slackProperties.setNotifyPropertyCreated(true);
        final Long groupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        userGroupRepository.updateWebhook(groupId, WEBHOOK);
    }

    @AfterEach
    void tearDown() {
        slackProperties.setEnabled(false);
        slackProperties.setNotifyPropertyCreated(false);
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("매물을 등록하면 알림이 나간다 — 이벤트가 리스너까지 닿아야 한다")
    void createReachesSlack() {
        propertyService.create(request("배선매물"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stub.messages).anySatisfy(text ->
                        assertThat(text).contains("새 매물").contains("배선매물")));
    }

    @Test
    @DisplayName("매물을 지우면 알림이 나간다")
    void deleteReachesSlack() {
        final Long id = propertyService.create(request("지울매물")).id();
        await().atMost(Duration.ofSeconds(5)).until(() -> !stub.messages.isEmpty());
        stub.messages.clear();

        propertyService.delete(id);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stub.messages).anySatisfy(text ->
                        assertThat(text).contains("삭제").contains("지울매물")));
    }

    @Test
    @DisplayName("등록 알림 스위치가 꺼져 있으면 안 나간다 — 삭제는 그대로 나간다")
    void createSwitchIsRespected() {
        slackProperties.setNotifyPropertyCreated(false);

        final Long id = propertyService.create(request("조용한매물")).id();
        propertyService.delete(id);

        // 삭제 알림이 도착했다는 것은 <b>배선이 살아 있다</b>는 뜻이다.
        // 그런데도 등록 알림이 없다면 스위치가 막은 것이지 배선이 끊긴 것이 아니다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stub.messages).anySatisfy(text -> assertThat(text).contains("삭제")));
        assertThat(stub.messages).noneSatisfy(text -> assertThat(text).contains("새 매물"));
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
