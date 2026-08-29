package banghak.home.halley.adapter.outbound.external.odsay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class OdsayTransitFallbackFactory implements FallbackFactory<OdsayTransitFeignClient> {

    @Override
    public OdsayTransitFeignClient create(Throwable cause) {
        return (apiKey, startX, startY, endX, endY) -> {
            log.warn("ODsay transit search failed - returning fallback (MISSING). start=({},{}), end=({},{}), cause={}",
                    startX, startY, endX, endY, describe(cause));
            return null;
        };
    }
}
