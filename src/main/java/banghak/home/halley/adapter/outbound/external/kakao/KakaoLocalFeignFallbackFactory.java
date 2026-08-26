package banghak.home.halley.adapter.outbound.external.kakao;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class KakaoLocalFeignFallbackFactory implements FallbackFactory<KakaoLocalFeignClient> {

    @Override
    public KakaoLocalFeignClient create(Throwable cause) {
        return new KakaoLocalFeignClient() {
            @Override
            public String searchAddress(String query) {
                return null;
            }

            @Override
            public String searchCategory(String categoryGroupCode, String x, String y, int radius) {
                return null;
            }
        };
    }
}
