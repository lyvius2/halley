package banghak.home.halley.adapter.outbound.external.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class SlackWebhookFallbackFactory implements FallbackFactory<SlackWebhookClient> {

    @Override
    public SlackWebhookClient create(Throwable cause) {
        return (webhookUrl, payload, contentType) -> {
            log.warn("Slack webhook post failed - returning fallback (recorded as FAILED in NOTIFICATION_LOG). cause={}", describe(cause));
            return null;
        };
    }
}
