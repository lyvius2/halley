package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.TravelMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 경로를 짜 달라는 요청. */
public record OptimizeItineraryRequest(
        List<Long> propertyIds,
        TravelMode travelMode,
        BigDecimal startLat,
        BigDecimal startLng,
        LocalDate visitDate,
        LocalTime windowStart,
        Integer stayMinutes
) {
}
