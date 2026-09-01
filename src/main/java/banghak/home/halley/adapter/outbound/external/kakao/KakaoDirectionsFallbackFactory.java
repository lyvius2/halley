package banghak.home.halley.adapter.outbound.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class KakaoDirectionsFallbackFactory implements FallbackFactory<KakaoDirectionsFeignClient> {

    @Override
    public KakaoDirectionsFeignClient create(Throwable cause) {
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
