package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.NotificationLogRepository;
import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationLog;
import banghak.home.halley.domain.notification.NotificationStatus;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class NotificationServiceTest {

    @TestConfiguration
    static class StubConfig {

        final java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean(false);

        @Bean
        @Primary
        SlackPort slackPort() {
            return text -> !fail.get();
        }
    }

    @Autowired
    private StubConfig stubConfig;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private SlackProperties slackProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void enableSlack() {
        stubConfig.fail.set(false);
        slackProperties.setEnabled(true);
        slackProperties.setNotifyPropertyCreated(true);
        slackProperties.setWebhookUrl("https://hooks.slack.com/services/T");
    }

    @Test
    @DisplayName("매물 등록 알림을 보내면 SENT 로그가 기록된다")
    void sendPropertyCreatedRecordsSent() {
        // given
        userService.create(new CreateUserRequest(
                "notify-user", "notify@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));
        final var property = propertyService.create(request("알림 테스트"));

        // when
        notificationService.sendPropertyCreated(property.id());

        // then
        final List<NotificationLog> logs = notificationLogRepository.findLatest(50);
        assertThat(logs).anyMatch(log ->
                log.propertyId().equals(property.id())
                        && log.status() == NotificationStatus.SENT);
    }

    @Test
    @DisplayName("존재하지 않는 매물이면 알림을 보내지 않고 로그도 남기지 않는다")
    void missingPropertySkips() {
        // when
        notificationService.sendPropertyCreated(999_999L);

        // then
        assertThat(notificationLogRepository.findLatest(50))
                .noneMatch(log -> log.propertyId() != null && log.propertyId().equals(999_999L));
    }

    @Test
    @DisplayName("재시도 대상(RETRYING) 알림을 재발송해 SENT로 만든다")
    void resendRetrying() {
        // given
        final NotificationLog retrying = notificationLogRepository.save(new NotificationLog(
                null, NotificationEventType.BATCH_SUMMARY, null, "slack",
                NotificationStatus.RETRYING, 1, null, objectMapper.createObjectNode(), null, null));

        // when
        notificationService.resendRetrying();

        // then
        assertThat(notificationLogRepository.findById(retrying.id()).orElseThrow().status())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("재시도 실패는 retryCount를 올리고 3회 미만이면 RETRYING을 유지한다")
    void retryFailureKeepsRetrying() {
        // given
        stubConfig.fail.set(true);
        final NotificationLog retrying = notificationLogRepository.save(new NotificationLog(
                null, NotificationEventType.BATCH_SUMMARY, null, "slack",
                NotificationStatus.RETRYING, 0, null, objectMapper.createObjectNode(), null, null));

        // when
        notificationService.resendRetrying();

        // then
        final NotificationLog after = notificationLogRepository.findById(retrying.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(after.retryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 3회 이상 실패하면 FAILED로 확정된다")
    void retryExhaustedMarksFailed() {
        // given
        stubConfig.fail.set(true);
        final NotificationLog retrying = notificationLogRepository.save(new NotificationLog(
                null, NotificationEventType.BATCH_SUMMARY, null, "slack",
                NotificationStatus.RETRYING, 2, null, objectMapper.createObjectNode(), null, null));

        // when
        notificationService.resendRetrying();

        // then
        final NotificationLog after = notificationLogRepository.findById(retrying.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(after.retryCount()).isEqualTo(3);
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null,
                null, null, null);
    }
}
