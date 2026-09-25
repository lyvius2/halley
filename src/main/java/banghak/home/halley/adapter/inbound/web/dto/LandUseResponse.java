package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;

import java.time.Instant;

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
