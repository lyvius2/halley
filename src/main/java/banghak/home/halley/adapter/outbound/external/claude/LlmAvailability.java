package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** 차단기가 열려 있는가. */
@Slf4j
@Component
public class LlmAvailability {

 /** 차단기가 막았다는 말. 원인 사슬에 이 말이 있으면 다시 물어도 소용없다 */
    private static final String BLOCKED = "does not permit further calls";
 /** resilience4j.circuitbreaker.instances.claude-llm.waitDurationInOpenState 와 같다. */
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
        log.warn("Claude circuit is open - not asking again for {}s.",
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
