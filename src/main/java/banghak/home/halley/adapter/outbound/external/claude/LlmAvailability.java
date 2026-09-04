package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 차단기가 열려 있는가 (설계 I271).
 *
 * <p>운영에서 이렇게 됐습니다.
 *
 * <pre>
 * CircuitBreaker 'claude-llm' is OPEN and does not permit further calls
 *   → "LLM is busy - waiting 2000ms and asking once more."
 *   → 또 OPEN → 또 2초 대기 → …  <b>몇 분 동안</b>
 * </pre>
 *
 * <p><b>열린 차단기에 다시 던지면 절대 성공하지 않습니다.</b> 그런데 어댑터가 모든
 * 실패를 {@code "call failed"} 하나로 뭉개서, 부르는 쪽은 "붐빈다"와 "차단됐다"를
 * 구별하지 못했습니다.
 *
 * <p>차단기가 스스로 반쯤 열어 볼 때까지만 기다립니다 — 설정과 같은 60초입니다.
 * 더 길게 잡으면 회복한 뒤에도 안 부르고, 짧게 잡으면 이 표시가 뜻이 없습니다.
 */
@Slf4j
@Component
public class LlmAvailability {

    /** 차단기가 막았다는 말. 원인 사슬에 이 말이 있으면 다시 물어도 소용없다 */
    private static final String BLOCKED = "does not permit further calls";
    /** `resilience4j.circuitbreaker.instances.claude-llm.waitDurationInOpenState` 와 같다. */
    private static final Duration OPEN_WINDOW = Duration.ofSeconds(60);

    private volatile Instant blockedUntil;

    public boolean blocked() {
        final Instant until = blockedUntil;
        return until != null && Instant.now().isBefore(until);
    }

    public void recordIfBlocked(Throwable cause) {
        if (cause == null || !mentionsBlocked(cause)) {
            return;
        }
        if (blocked()) {
            return;
        }
        blockedUntil = Instant.now().plus(OPEN_WINDOW);
        log.warn("Claude circuit is open - not asking again for {}s (설계 I271).",
                OPEN_WINDOW.toSeconds());
    }

    private static boolean mentionsBlocked(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            final String message = t.getMessage();
            if (message != null && message.contains(BLOCKED)) {
                return true;
            }
        }
        return false;
    }
}
