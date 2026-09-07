package banghak.home.halley.adapter.outbound.external.odsay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/** 오퍼레이션이 둘이 되어 람다로는 안 됩니다. 익명 클래스로 각각 로그를 남깁니다 */
@Slf4j
@Component
public class OdsayTransitFallbackFactory implements FallbackFactory<OdsayTransitFeignClient> {

    @Override
    public OdsayTransitFeignClient create(Throwable cause) {
        return new OdsayTransitFeignClient() {

            @Override
            public String findTransit(String apiKey, double startX, double startY,
                                      double endX, double endY) {
                log.warn("ODsay transit search failed - returning fallback (MISSING). "
                                + "start=({},{}), end=({},{}), cause={}",
                        startX, startY, endX, endY, describe(cause));
                return null;
            }

            @Override
            public String loadLane(String apiKey, String mapObject) {
                log.warn("ODsay lane lookup failed - the map will fall back to a straight line. "
                        + "mapObject={}, cause={}", mapObject, describe(cause));
                return null;
            }
        };
    }
}
