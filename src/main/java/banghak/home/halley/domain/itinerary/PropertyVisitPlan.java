package banghak.home.halley.domain.itinerary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record PropertyVisitPlan(
        Long id,
        LocalDate visitDate,
        Long createdBy,
        String startAddress,
        BigDecimal startLat,
        BigDecimal startLng,
        TravelMode travelMode,
        LocalTime windowStart,
        LocalTime windowEnd,
        Integer stayMinutesDefault,
        PlanStatus status,
        Instant computedAt
) {
}
