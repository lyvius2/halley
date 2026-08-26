package banghak.home.halley.adapter.outbound.external.ministry;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class MinistryReferenceFallbackFactory implements FallbackFactory<MinistryReferenceFeignClient> {

    @Override
    public MinistryReferenceFeignClient create(Throwable cause) {
        return new MinistryReferenceFeignClient() {
            @Override
            public String fetchTrade(String serviceKey, String lawdCd, String dealYmd) {
                return null;
            }
        };
    }
}
