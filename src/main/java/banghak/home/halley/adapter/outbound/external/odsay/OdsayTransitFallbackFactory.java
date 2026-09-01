package banghak.home.halley.adapter.outbound.external.odsay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

/**
 * 오퍼레이션이 둘이 되어 람다로는 안 됩니다 — 익명 클래스로 각각 로그를 남깁니다
 * (설계 I131과 같은 이유). 어느 쪽이 실패했는지 구분되지 않으면 원인을 못 찾습니다.
 */
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
                // 경로선이 없으면 지도에 직선을 그린다 (설계 I177). 화면이 막히지는 않는다
                log.warn("ODsay lane lookup failed - the map will fall back to a straight line. "
                        + "mapObject={}, cause={}", mapObject, describe(cause));
                return null;
            }
        };
    }
}
