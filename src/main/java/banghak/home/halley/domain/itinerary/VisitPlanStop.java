package banghak.home.halley.domain.itinerary;

import java.time.Instant;
import java.time.LocalTime;

public record VisitPlanStop(
        Long id,
        Long planId,
        Long propertyId,
        Integer stopOrder,
        LocalTime estimatedArrival,
        LocalTime estimatedDeparture,
        Integer travelMinutesFromPrev,
        String travelModeSegment,
        boolean visited,
        Instant visitedAt
) {
}
