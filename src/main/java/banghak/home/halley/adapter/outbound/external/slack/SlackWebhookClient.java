package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

/** Slack 수신 웹훅. */
@FeignClient(name = "slack-webhook",
        url = "https://hooks.slack.com",
        fallbackFactory = SlackWebhookFallbackFactory.class)
public interface SlackWebhookClient {

 /** Content-Type 을 파라미터로 넘기지 않습니다. */
    @PostMapping(consumes = "application/json;charset=UTF-8")
    String post(URI webhookUrl, @RequestBody String payload);
}
