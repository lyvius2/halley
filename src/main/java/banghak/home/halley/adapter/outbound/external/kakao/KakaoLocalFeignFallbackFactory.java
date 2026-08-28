package banghak.home.halley.adapter.outbound.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class KakaoLocalFeignFallbackFactory implements FallbackFactory<KakaoLocalFeignClient> {

    @Override
    public KakaoLocalFeignClient create(Throwable cause) {
        return new KakaoLocalFeignClient() {
            @Override
            public String searchAddress(String query) {
                log.warn("Kakao local address search failed - returning fallback (empty). query={}, cause={}", query, describe(cause));
                return null;
            }

            @Override
            public String searchCategory(String categoryGroupCode, String x, String y, int radius) {
                log.warn("Kakao local category search failed - returning fallback (empty). code={}, cause={}",
                        categoryGroupCode, describe(cause));
                return null;
            }

            @Override
            public String searchKeyword(String query, String categoryGroupCode, String x, String y, int radius, String sort) {
                log.warn("Kakao local keyword search failed - returning fallback (empty). query={}, cause={}",
                        query, describe(cause));
                return null;
            }
        };
    }
}
