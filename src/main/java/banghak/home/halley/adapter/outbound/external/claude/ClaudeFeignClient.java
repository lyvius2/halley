package banghak.home.halley.adapter.outbound.external.claude;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Anthropic Messages API (설계 I58).
 * 인증은 `x-api-key`이고 `anthropic-version` 헤더가 필수다.
 *
 * <p><b>모델 목록은 여기 두지 않습니다</b> (설계 I267). 메서드가 둘이 되면 이
 * 인터페이스가 함수형이 아니게 되어, 대역을 람다로 만들던 자리가 전부 깨집니다.
 * 무엇보다 성격이 다릅니다 — 하나는 매번 부르는 생성이고 하나는 이틀에 한 번
 * 부르는 조회입니다.
 */
@FeignClient(name = "claude-llm",
        url = "${llm.claude.base-url:https://api.anthropic.com}",
        fallbackFactory = ClaudeFallbackFactory.class)
public interface ClaudeFeignClient {

    @PostMapping(value = "/v1/messages", consumes = "application/json")
    String messages(@RequestHeader("x-api-key") String apiKey,
                    @RequestHeader("anthropic-version") String version,
                    @RequestBody String body);
}
