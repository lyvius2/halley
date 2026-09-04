package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/** 목록을 못 받으면 비워 돌려준다 — 화면은 그때 지금 쓰는 모델만 보여 준다 (설계 I267). */
@Slf4j
@Component
public class ClaudeModelsFallbackFactory implements FallbackFactory<ClaudeModelsFeignClient> {

    @Override
    public ClaudeModelsFeignClient create(Throwable cause) {
        return (apiKey, version, limit) -> {
            log.warn("Claude model list failed - returning fallback (empty). cause={}", describe(cause));
            return null;
        };
    }
}
