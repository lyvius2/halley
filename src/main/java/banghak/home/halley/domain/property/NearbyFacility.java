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
}
