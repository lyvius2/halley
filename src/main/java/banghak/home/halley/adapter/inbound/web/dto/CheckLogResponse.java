package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.property.ListingVerdict;

import java.time.Instant;

public record CheckLogResponse(
        Long id,
        Instant checkedAt,
        Integer httpStatus,
        ListingVerdict verdict,
        String evidence,
        Integer elapsedMs
) {
}
