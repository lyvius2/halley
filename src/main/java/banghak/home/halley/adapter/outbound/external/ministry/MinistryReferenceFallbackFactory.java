package banghak.home.halley.adapter.outbound.external.ministry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class MinistryReferenceFallbackFactory implements FallbackFactory<MinistryReferenceFeignClient> {

    @Override
    public MinistryReferenceFeignClient create(Throwable cause) {
        return new MinistryReferenceFeignClient() {

            @Override
            public String fetchTrade(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                log.warn("Ministry trade lookup failed - returning empty. lawdCd={}, dealYmd={}, cause={}",
                        lawdCd, dealYmd, describe(cause));
                return null;
            }

            @Override
            public String fetchRent(String serviceKey, String lawdCd, String dealYmd, int numOfRows) {
                log.warn("Ministry rent lookup failed - returning empty. lawdCd={}, dealYmd={}, cause={}",
                        lawdCd, dealYmd, describe(cause));
                return null;
            }
        };
    }
}
