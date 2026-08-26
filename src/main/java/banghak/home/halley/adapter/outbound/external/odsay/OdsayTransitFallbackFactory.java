package banghak.home.halley.adapter.outbound.external.odsay;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class OdsayTransitFallbackFactory implements FallbackFactory<OdsayTransitFeignClient> {

    @Override
    public OdsayTransitFeignClient create(Throwable cause) {
        return new OdsayTransitFeignClient() {
            @Override
            public String findTransit(String apiKey, double startX, double startY, double endX, double endY) {
                return null;
            }
        };
    }
}
