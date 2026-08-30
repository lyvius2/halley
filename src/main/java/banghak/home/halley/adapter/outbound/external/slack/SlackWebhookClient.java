package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.net.URI;

/**
 * Slack 수신 웹훅 (설계 I96).
 *
 * <p><b>주소를 호출할 때 받습니다.</b> 그룹마다 보낼 곳이 다른데 `url` 속성에 굽으면
 * 기동 시점에 하나로 고정됩니다. 첫 인자로 {@link URI}를 받으면 Feign이 그쪽으로 보냅니다.
 */
@FeignClient(name = "slack-webhook",
        url = "https://hooks.slack.com",
        fallbackFactory = SlackWebhookFallbackFactory.class)
public interface SlackWebhookClient {

    @PostMapping
    String post(URI webhookUrl,
                @RequestBody String payload,
                @RequestHeader("Content-Type") String contentType);
}
