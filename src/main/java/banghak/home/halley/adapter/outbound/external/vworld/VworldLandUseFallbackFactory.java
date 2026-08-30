package banghak.home.halley.adapter.outbound.external.vworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class VworldLandUseFallbackFactory implements FallbackFactory<VworldLandUseFeignClient> {

    @Override
    public VworldLandUseFeignClient create(Throwable cause) {
        return (key, pnu, format, numOfRows, pageNo) -> {
            log.warn("VWorld land use lookup failed - returning fallback (no land use). pnu={}, cause={}",
                    pnu, describe(cause));
            return null;
        };
    }
}
