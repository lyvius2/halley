package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "slack-webhook",
        url = "${slack.webhook-url:}",
        fallbackFactory = SlackWebhookFallbackFactory.class)
public interface SlackWebhookClient {

    @PostMapping
    String post(@RequestBody String payload,
                @RequestHeader("Content-Type") String contentType);
}
