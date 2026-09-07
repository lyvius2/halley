package banghak.home.halley.domain.landuse;

import java.time.Instant;

/** 매물 필지에 걸린 지역·지구 한 건. */
public record LandUse(
        Long id,
        Long propertyId,
        String zoneCode,
        String zoneName,
        LandUseConflict conflict,
        String pnu,
        Instant fetchedAt
) {

 /** 매수 판단에 직접 영향을 주는 항목인지. */
    public boolean isNotable() {
        if (conflict != LandUseConflict.INCLUDED || zoneName == null) {
            return false;
        }
        return NOTABLE_KEYWORDS.stream().anyMatch(zoneName::contains);
    }

 /** 토지거래허가구역. 매매에 구청 허가가 필요하고 실거주 의무가 붙어 갭투자가 불가능합니다. */
    private static final java.util.List<String> NOTABLE_KEYWORDS = java.util.List.of(
            "토지거래계약에관한허가구역", "정비구역", "개발행위허가제한지역");
}
