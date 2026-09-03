package banghak.home.halley.adapter.outbound.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class KakaoDirectionsFallbackFactory implements FallbackFactory<KakaoDirectionsFeignClient> {

    private final DirectionsQuota quota;

    public KakaoDirectionsFallbackFactory(DirectionsQuota quota) {
        this.quota = quota;
    }

    @Override
    public KakaoDirectionsFeignClient create(Throwable cause) {
        // 한도 때문이면 <b>그날은 더 부르지 않는다</b> (설계 I270)
        quota.recordIfExhausted(cause);
        return new KakaoDirectionsFeignClient() {

            @Override
            public String directions(String origin, String destination, String priority) {
                return warn(origin, destination, "now");
            }

            @Override
            public String futureDirections(String origin, String destination, String priority,
                                           String departureTime) {
                return warn(origin, destination, departureTime);
            }

            private String warn(String origin, String destination, String when) {
                log.warn("Kakao Directions failed - returning fallback (travel time unknown). "
                                + "origin={}, destination={}, departureTime={}, cause={}",
                        origin, destination, when, describe(cause));
                return null;
            }
        };
    }
}
