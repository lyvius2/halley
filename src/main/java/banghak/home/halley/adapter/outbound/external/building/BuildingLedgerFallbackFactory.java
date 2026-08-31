package banghak.home.halley.adapter.outbound.external.building;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class BuildingLedgerFallbackFactory implements FallbackFactory<BuildingLedgerFeignClient> {

    @Override
    public BuildingLedgerFeignClient create(Throwable cause) {
        return (serviceKey, sigunguCd, bjdongCd, platGbCd, bun, ji, type, numOfRows) -> {
            log.warn("Building ledger lookup failed - returning empty. "
                            + "sigunguCd={}, bjdongCd={}, bun={}, ji={}, cause={}",
                    sigunguCd, bjdongCd, bun, ji, describe(cause));
            return null;
        };
    }
}
