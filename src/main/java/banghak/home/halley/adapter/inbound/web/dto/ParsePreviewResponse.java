package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

public record ParsePreviewResponse(
        boolean isListing,
        List<ParsedFieldResponse> fields
) {
}
