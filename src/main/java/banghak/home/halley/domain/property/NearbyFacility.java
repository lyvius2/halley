package banghak.home.halley.domain.property;

import java.time.Instant;

/** 매물 주변 POI를 나타내는 채점 입력값이다. */
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
