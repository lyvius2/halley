package banghak.home.halley.adapter.outbound.external.claude;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 쓸 수 있는 모델 목록 (설계 I267).
 *
 * <p>손으로 적어 두면 <b>늙습니다.</b> 모델이 바뀔 때마다 배포해야 하고,
 * 이미 없어진 이름을 고를 수 있게 됩니다.
 */
@FeignClient(name = "claude-models",
        url = "${llm.claude.base-url:https://api.anthropic.com}",
        fallbackFactory = ClaudeModelsFallbackFactory.class)
public interface ClaudeModelsFeignClient {

    @GetMapping("/v1/models")
    String models(@RequestHeader("x-api-key") String apiKey,
                  @RequestHeader("anthropic-version") String version,
                  @RequestParam("limit") int limit);
}
