package banghak.home.halley.application.service;

import banghak.home.halley.domain.notification.NotificationEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slack 메시지에 붙는 것 (설계 I189 · I191).
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "app.base-url=https://halley.example.com/")
@DisplayName("Slack 메시지 꾸미기 (설계 I189·I191)")
class SlackMessageDecorationTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("채널 전체를 부른다 — 흘러가면 아무도 안 본다")
    void mentionsChannel() {
        final String text = notificationService.decorate(
                NotificationEventType.PROPERTY_CREATED, 7L, ":house: 새 매물");

        assertThat(text).startsWith("<!channel> ");
    }

    @Test
    @DisplayName("매물 링크를 줄 바꿔 붙인다 — 알림에서 바로 그 매물로 간다")
    void appendsPropertyLink() {
        final String text = notificationService.decorate(
                NotificationEventType.PROPERTY_CREATED, 7L, ":house: 새 매물");

        assertThat(text).contains("\n https://halley.example.com/properties/7".trim());
        // 끝의 슬래시가 겹치면 //properties 가 된다
        assertThat(text).doesNotContain("com//properties");
    }

    /** 이미 없는 매물로 보내면 <b>404 를 여는 링크</b>가 된다. */
    @Test
    @DisplayName("삭제 알림에는 링크를 안 단다 — 이미 없는 매물이다")
    void noLinkForDeleted() {
        final String text = notificationService.decorate(
                NotificationEventType.PROPERTY_DELETED, 7L, ":wastebasket: 삭제");

        assertThat(text).startsWith("<!channel> ");
        assertThat(text).doesNotContain("/properties/");
    }

    @Test
    @DisplayName("매물에 딸리지 않은 알림에는 링크가 없다")
    void noLinkWithoutProperty() {
        assertThat(notificationService.decorate(NotificationEventType.PROPERTY_CREATED, null, "x"))
                .doesNotContain("/properties/");
    }
}
