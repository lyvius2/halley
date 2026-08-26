package banghak.home.halley.adapter.outbound.external.slack;

import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SlackWebhookAdapter implements SlackPort {

    private final SlackWebhookClient client;
    private final SlackProperties slackProperties;
    private final ObjectMapper objectMapper;

    public SlackWebhookAdapter(SlackWebhookClient client,
                               SlackProperties slackProperties,
                               ObjectMapper objectMapper) {
        this.client = client;
        this.slackProperties = slackProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(String text) {
        if (slackProperties.getWebhookUrl() == null || slackProperties.getWebhookUrl().isBlank()) {
            return false;
        }
        try {
            final String payload = objectMapper.createObjectNode().put("text", text).toString();
            return client.post(payload, "application/json;charset=UTF-8") != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
