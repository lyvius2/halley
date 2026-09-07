package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/** 임장 플래너에서 작업 중인 것. */
public record ItineraryDraft(
        List<Long> propertyIds,
        String travelMode,
        OptimizeItineraryResponse result
) {

    public static ItineraryDraft empty() {
        return new ItineraryDraft(List.of(), null, null);
    }
}
