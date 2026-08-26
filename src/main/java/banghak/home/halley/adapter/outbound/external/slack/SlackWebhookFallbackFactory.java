package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class SlackWebhookFallbackFactory implements FallbackFactory<SlackWebhookClient> {

    @Override
    public SlackWebhookClient create(Throwable cause) {
        return new SlackWebhookClient() {
            @Override
            public String post(String payload, String contentType) {
                return null;
            }
        };
    }
}
