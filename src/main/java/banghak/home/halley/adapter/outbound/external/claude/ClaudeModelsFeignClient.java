package banghak.home.halley.adapter.outbound.external.claude;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** 쓸 수 있는 모델 목록 (설계 I267). */
@FeignClient(name = "claude-models",
        url = "${llm.claude.base-url:https://api.anthropic.com}",
        fallbackFactory = ClaudeModelsFallbackFactory.class)
public interface ClaudeModelsFeignClient {

    @GetMapping("/v1/models")
    String models(@RequestHeader("x-api-key") String apiKey,
                  @RequestHeader("anthropic-version") String version,
                  @RequestParam("limit") int limit);
}
