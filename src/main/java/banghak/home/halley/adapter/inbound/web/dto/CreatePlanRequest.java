package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.TravelMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreatePlanRequest(
        List<Long> propertyIds,
        TravelMode travelMode,
        BigDecimal startLat,
        BigDecimal startLng,
        String startAddress,
        LocalDate visitDate,
        LocalTime windowStart,
        LocalTime windowEnd,
        Integer stayMinutesDefault
) {
}
