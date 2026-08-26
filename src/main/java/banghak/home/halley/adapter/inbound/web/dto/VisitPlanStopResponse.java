package banghak.home.halley.adapter.inbound.web.dto;

import java.time.LocalTime;

public record VisitPlanStopResponse(
        Long id,
        Long propertyId,
        Integer stopOrder,
        LocalTime estimatedArrival,
        LocalTime estimatedDeparture,
        Integer travelMinutesFromPrev,
        boolean visited
) {
}
