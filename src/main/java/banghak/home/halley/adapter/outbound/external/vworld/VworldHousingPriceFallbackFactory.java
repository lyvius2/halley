package banghak.home.halley.adapter.outbound.external.vworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class VworldHousingPriceFallbackFactory implements FallbackFactory<VworldHousingPriceFeignClient> {

    @Override
    public VworldHousingPriceFeignClient create(Throwable cause) {
        return new VworldHousingPriceFeignClient() {
            @Override
            public String fetchApartmentPrice(String key, String pnu, String stdrYear, String format,
                                              int numOfRows, int pageNo, String domain) {
                log.warn("VWorld apartment price lookup failed - returning fallback (no price). pnu={}, cause={}",
                        pnu, describe(cause));
                return null;
            }

            @Override
            public String fetchDetachedHousePrice(String key, String pnu, String stdrYear, String format,
                                                  int numOfRows, int pageNo, String domain) {
                log.warn("VWorld detached house price lookup failed - returning fallback (no price). pnu={}, cause={}",
                        pnu, describe(cause));
                return null;
            }
        };
    }
}
