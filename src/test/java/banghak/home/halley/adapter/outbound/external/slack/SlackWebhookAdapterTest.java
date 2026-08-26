package banghak.home.halley.adapter.outbound.external.slack;

import banghak.home.halley.config.SlackProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SlackWebhookAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("웹훅이 응답을 주면 성공(true)을 반환한다")
    void sendSuccess() {
        // given
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (payload, contentType) -> "ok", props("https://hooks.slack.com/services/T"), objectMapper);

        // when
        final boolean sent = adapter.send("테스트");

        // then
        assertThat(sent).isTrue();
    }

    @Test
    @DisplayName("Feign 폴백(응답 null)이면 실패(false)를 반환한다")
    void sendFailureOnFallback() {
        // given
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (payload, contentType) -> null, props("https://hooks.slack.com/services/T"), objectMapper);

        // when
        final boolean sent = adapter.send("테스트");

        // then
        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("웹훅 URL이 비어 있으면 보내지 않고 false를 반환한다")
    void blankUrlSkips() {
        // given
        final SlackWebhookAdapter adapter = new SlackWebhookAdapter(
                (payload, contentType) -> "ok", props(""), objectMapper);

        // when
        final boolean sent = adapter.send("테스트");

        // then
        assertThat(sent).isFalse();
    }

    private static SlackProperties props(String url) {
        final SlackProperties props = new SlackProperties();
        props.setWebhookUrl(url);
        return props;
    }
}
