package banghak.home.halley.domain.property;

import java.time.Instant;

public record ListingCheckLog(
        Long id,
        Long propertyId,
        Instant checkedAt,
        Integer httpStatus,
        ListingVerdict verdict,
        String evidence,
        Integer elapsedMs,
        boolean notified
) {
}
