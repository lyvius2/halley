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
        return (origin, destination, priority) -> {
            log.warn("Kakao Directions failed - returning fallback (travel time unknown). origin={}, destination={}, cause={}",
                    origin, destination, describe(cause));
            return null;
        };
    }
}
