package banghak.home.halley.adapter.outbound.external.slack;

import banghak.home.halley.application.port.out.external.SlackPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/** Slack 수신 웹훅 어댑터. */
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
            log.warn("Slack send failed. cause={}", e.toString());
            return false;
        }
    }
}
