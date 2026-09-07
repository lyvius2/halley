package banghak.home.halley.adapter.outbound.external.slack;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

/**
 * Slack 수신 웹훅.
 *
 * 주소를 호출할 때 받습니다. 그룹마다 보낼 곳이 다른데 `url` 속성에 굽으면
 * 기동 시점에 하나로 고정됩니다. 첫 인자로 {@link URI}를 받으면 Feign이 그쪽으로 보냅니다.
 */
@FeignClient(name = "slack-webhook",
        url = "https://hooks.slack.com",
        fallbackFactory = SlackWebhookFallbackFactory.class)
public interface SlackWebhookClient {

    /**
     * Content-Type 을 파라미터로 넘기지 않습니다.
     *
     * `@RequestHeader("Content-Type")` 로 두었더니 인코더가 치환 전 자리표시자를
     * 읽었습니다.
     *
     *
     * EncodeException: Invalid mime type "{Content-Type}": does not contain '/'
     *
     *
     * 인코더는 본문을 만들기 전에 Content-Type 을 봐야 하는데, 그 시점에는
     * 파라미터가 아직 안 채워져 있습니다. `consumes` 로 두면 고정 헤더가 되어
     * 그 문제가 없습니다.
     *
     * `charset=UTF-8` 은 못 박아 두는 쪽입니다. 빼고 돌려 봤더니 지금도
     * UTF-8 로 나갑니다(`application/json` 이면 Jackson 컨버터가 씁니다) —
     * 다만 컨버터가 바뀌면 그 보장이 사라지므로 명시해 둡니다.
     */
    @PostMapping(consumes = "application/json;charset=UTF-8")
    String post(URI webhookUrl, @RequestBody String payload);
}
