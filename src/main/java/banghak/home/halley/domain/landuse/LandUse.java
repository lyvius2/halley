package banghak.home.halley.domain.landuse;

import java.time.Instant;

public record LandUse(
        Long id,
        Long propertyId,
        String zoneCode,
        String zoneName,
        LandUseConflict conflict,
        String pnu,
        Instant fetchedAt
) {

    public boolean isNotable() {
        if (conflict != LandUseConflict.INCLUDED || zoneName == null) {
            return false;
        }
        return NOTABLE_KEYWORDS.stream().anyMatch(zoneName::contains);
    }

    private static final java.util.List<String> NOTABLE_KEYWORDS = java.util.List.of(
            "토지거래계약에관한허가구역", "정비구역", "개발행위허가제한지역");
}
