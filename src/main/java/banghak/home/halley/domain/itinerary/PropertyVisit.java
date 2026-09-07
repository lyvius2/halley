package banghak.home.halley.domain.itinerary;

import java.time.Instant;

/** 그 매물에 가 봤다. */
public record PropertyVisit(
        Long id,
        Long propertyId,
        Long userId,
        Instant visitedAt
) {
}
