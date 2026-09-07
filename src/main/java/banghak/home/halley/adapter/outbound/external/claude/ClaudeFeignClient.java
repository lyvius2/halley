package banghak.home.halley.adapter.outbound.external.claude;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Anthropic Messages API. */
@FeignClient(name = "claude-llm",
        url = "${llm.claude.base-url:https://api.anthropic.com}",
        fallbackFactory = ClaudeFallbackFactory.class)
public interface ClaudeFeignClient {

    @PostMapping(value = "/v1/messages", consumes = "application/json")
    String messages(@RequestHeader("x-api-key") String apiKey,
                    @RequestHeader("anthropic-version") String version,
                    @RequestBody String body);
}
