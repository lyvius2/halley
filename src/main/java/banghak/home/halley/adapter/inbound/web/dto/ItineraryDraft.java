package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

public record ItineraryDraft(
        List<Long> propertyIds,
        String travelMode,
        OptimizeItineraryResponse result
) {

    public static ItineraryDraft empty() {
        return new ItineraryDraft(List.of(), null, null);
    }
}
