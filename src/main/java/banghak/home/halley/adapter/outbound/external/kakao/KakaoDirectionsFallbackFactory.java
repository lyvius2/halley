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
            log.warn("카카오 Directions 실패 — 폴백(이동시간 미상) 반환. origin={}, destination={}, cause={}",
                    origin, destination, describe(cause));
            return null;
        };
    }
}
