package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

@FeignClient(name = "slack-webhook",
        url = "https://hooks.slack.com",
        fallbackFactory = SlackWebhookFallbackFactory.class)
public interface SlackWebhookClient {

    @PostMapping(consumes = "application/json;charset=UTF-8")
    String post(URI webhookUrl, @RequestBody String payload);
}
