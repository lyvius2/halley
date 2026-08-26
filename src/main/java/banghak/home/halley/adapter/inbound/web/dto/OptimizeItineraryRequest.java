package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.TravelMode;

import java.math.BigDecimal;
import java.util.List;

public record OptimizeItineraryRequest(
        List<Long> propertyIds,
        TravelMode travelMode,
        BigDecimal startLat,
        BigDecimal startLng
) {
}
