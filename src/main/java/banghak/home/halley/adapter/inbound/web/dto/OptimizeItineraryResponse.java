package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

public record OptimizeItineraryResponse(
        List<Long> orderedPropertyIds,
        int totalMinutes
) {
}
