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
        return (serviceKey, lawdCd, dealYmd) -> {
            log.warn("Ministry transaction lookup failed - returning fallback (empty card). lawdCd={}, dealYmd={}, cause={}",
                    lawdCd, dealYmd, describe(cause));
            return null;
        };
    }
}
