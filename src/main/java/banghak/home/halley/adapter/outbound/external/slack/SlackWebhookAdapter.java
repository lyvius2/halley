package banghak.home.halley.adapter.outbound.external.slack;

import banghak.home.halley.application.port.out.external.SlackPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/**
 * Slack 수신 웹훅 어댑터 (설계 I96).
 *
 * <p><b>웹훅 주소를 로그에 남기지 않습니다.</b> 그 주소를 아는 사람은 누구나 그 채널에 글을
 * 쓸 수 있어 비밀번호에 가깝습니다.
 */
@Slf4j
@Component
public class SlackWebhookAdapter implements SlackPort {

    private final SlackWebhookClient client;
    private final ObjectMapper objectMapper;

    public SlackWebhookAdapter(SlackWebhookClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(String webhookUrl, String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        try {
            final String payload = objectMapper.createObjectNode().put("text", text).toString();
            return client.post(URI.create(webhookUrl.trim()), payload) != null;
        } catch (RuntimeException e) {
            // 주소는 찍지 않는다
            log.warn("Slack send failed. cause={}", e.toString());
            return false;
        }
    }
}
