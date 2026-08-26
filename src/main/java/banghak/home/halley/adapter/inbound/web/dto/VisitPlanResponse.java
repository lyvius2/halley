package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.PlanStatus;
import banghak.home.halley.domain.itinerary.TravelMode;

import java.time.LocalDate;
import java.util.List;

public record VisitPlanResponse(
        Long id,
        LocalDate visitDate,
        TravelMode travelMode,
        PlanStatus status,
        String startAddress,
        List<VisitPlanStopResponse> stops,
        int totalMinutes
) {
}
