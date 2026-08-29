package banghak.home.halley.adapter.outbound.external.claude;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class ClaudeFallbackFactory implements FallbackFactory<ClaudeFeignClient> {

    @Override
    public ClaudeFeignClient create(Throwable cause) {
        return (apiKey, version, body) -> {
            log.warn("Claude call failed - returning fallback (no recommendation). cause={}", describe(cause));
            return null;
        };
    }
}
