package banghak.home.halley.adapter.outbound.external.slack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slack 웹훅 어댑터 (설계 I96)")
class SlackWebhookAdapterTest {

    private static final String WEBHOOK = "https://hooks.slack.com/services/T/B/xyz";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("호출할 때 받은 주소로 보낸다 — 그룹마다 보낼 곳이 다르다")
    void sendsToTheGivenWebhook() {
        // given
        final AtomicReference<URI> target = new AtomicReference<>();
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (url, payload) -> {
                    target.set(url);
                    return "ok";
                }, objectMapper);

        // when
        final boolean sent = adapter.send(WEBHOOK, "테스트");

        // then
        assertThat(sent).isTrue();
        assertThat(target.get()).isEqualTo(URI.create(WEBHOOK));
    }

    @Test
    @DisplayName("Feign 폴백(응답 null)이면 실패로 본다")
    void sendFailureOnFallback() {
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (url, payload) -> null, objectMapper);

        assertThat(adapter.send(WEBHOOK, "테스트")).isFalse();
    }

    @Test
    @DisplayName("주소가 없으면 보내지 않는다 — 전역 주소로 흘려보내면 그게 누수다")
    void blankUrlSkips() {
        final AtomicReference<URI> target = new AtomicReference<>();
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (url, payload) -> {
                    target.set(url);
                    return "ok";
                }, objectMapper);

        assertThat(adapter.send(null, "테스트")).isFalse();
        assertThat(adapter.send("  ", "테스트")).isFalse();
        assertThat(target.get()).isNull();
    }
}
