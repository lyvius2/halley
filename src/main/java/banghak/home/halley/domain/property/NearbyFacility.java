package banghak.home.halley.domain.property;

import java.time.Instant;

public record NearbyFacility(
        Long id,
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
        return new NearbyFacility(null, propertyId, category, subCategory, name, distanceM, walkMinutes, fetchedAt);
    }
}
