package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;

import java.time.Instant;

/**
 * 토지이용계획 한 건 (설계 I69).
 *
 * @param conflict 필지와의 관계. <b>INCLUDED만 실제로 적용된다</b>
 * @param notable  매수 판단에 직접 영향을 주는 항목인지 — 화면에서 강조한다
 */
public record LandUseResponse(
        String zoneCode,
        String zoneName,
        LandUseConflict conflict,
        String conflictLabel,
        boolean notable,
        Instant fetchedAt
) {

    public static LandUseResponse from(LandUse l) {
        return new LandUseResponse(l.zoneCode(), l.zoneName(), l.conflict(),
                l.conflict().label(), l.isNotable(), l.fetchedAt());
    }
}
