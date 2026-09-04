package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class ClaudeFallbackFactory implements FallbackFactory<ClaudeFeignClient> {

    private final LlmAvailability availability;

    public ClaudeFallbackFactory(LlmAvailability availability) {
        this.availability = availability;
    }

    @Override
    public ClaudeFeignClient create(Throwable cause) {
        // 차단기가 막은 것이면 <b>다시 물어도 소용없다</b> (설계 I271)
        availability.recordIfBlocked(cause);
        return (apiKey, version, body) -> {
            log.warn("Claude call failed - returning fallback (no recommendation). cause={}", describe(cause));
            return null;
        };
    }
}
