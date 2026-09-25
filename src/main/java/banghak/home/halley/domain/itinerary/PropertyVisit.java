package banghak.home.halley.domain.itinerary;

import java.time.Instant;

public record PropertyVisit(
        Long id,
        Long propertyId,
        Long userId,
        Instant visitedAt
) {
}
