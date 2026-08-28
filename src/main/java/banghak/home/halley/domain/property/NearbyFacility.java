package banghak.home.halley.domain.property;

import java.time.Instant;

/**
 * 매물 주변 POI. 영속 테이블이 아니라 `PoiCache`(Redis/인메모리)에 담기는 채점 입력이다 — 설계 I44.
 */
public record NearbyFacility(
        Long propertyId,
        String category,
        String subCategory,
        String name,
        Integer distanceM,
        Integer walkMinutes,
        Instant fetchedAt
) {

    public static NearbyFacility of(Long propertyId, String category, String subCategory, String name,
                                    Integer distanceM, Integer walkMinutes, Instant fetchedAt) {
        return new NearbyFacility(propertyId, category, subCategory, name, distanceM, walkMinutes, fetchedAt);
    }
}
