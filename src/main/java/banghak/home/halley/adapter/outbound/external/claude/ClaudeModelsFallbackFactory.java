package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/**
 * 목록을 못 받으면 <b>비워 돌려줍니다</b> (설계 I267).
 *
 * <p>화면은 그때 <b>지금 쓰는 모델만</b> 보여 줍니다 — 빈 드롭다운을 주면
 * 고칠 수도 없고, 지어낸 이름을 채우면 그 자리의 AI가 조용히 죽습니다.
 */
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
