package banghak.home.halley.adapter.outbound.external.kakao;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class KakaoDirectionsFallbackFactory implements FallbackFactory<KakaoDirectionsFeignClient> {

    @Override
    public KakaoDirectionsFeignClient create(Throwable cause) {
        return new KakaoDirectionsFeignClient() {
            @Override
            public String directions(String origin, String destination, String priority) {
                return null;
            }
        };
    }
}
