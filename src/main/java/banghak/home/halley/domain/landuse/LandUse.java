package banghak.home.halley.domain.landuse;

import java.time.Instant;

/**
 * 매물 필지에 걸린 지역·지구 한 건 (설계 I69).
 *
 * @param zoneCode V-World `prposAreaDstrcCode` (예: UQA123 = 제3종일반주거지역)
 * @param zoneName 지역·지구명
 */
public record LandUse(
        Long id,
        Long propertyId,
        String zoneCode,
        String zoneName,
        LandUseConflict conflict,
        String pnu,
        Instant fetchedAt
) {

    /**
     * 매수 판단에 직접 영향을 주는 항목인지 (설계 I69).
     *
     * <p>35건이 통째로 나오는데 대부분은 도로·공원 계획선처럼 매수자와 무관합니다.
     * <b>실제로 살지 말지를 가르는 것만</b> 강조합니다.
     */
    public boolean isNotable() {
        if (conflict != LandUseConflict.INCLUDED || zoneName == null) {
            return false;
        }
        return NOTABLE_KEYWORDS.stream().anyMatch(zoneName::contains);
    }

    /**
     * 토지거래허가구역 — 매매에 구청 허가가 필요하고 실거주 의무가 붙어 <b>갭투자가 불가능</b>합니다.
     * 정비구역 — 재건축·재개발 구역이라 시세 형성의 핵심 변수입니다.
     */
    private static final java.util.List<String> NOTABLE_KEYWORDS = java.util.List.of(
            "토지거래계약에관한허가구역", "정비구역", "개발행위허가제한지역");
}
