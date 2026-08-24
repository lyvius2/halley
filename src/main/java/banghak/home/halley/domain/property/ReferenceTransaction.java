package banghak.home.halley.domain.property;

import java.time.Instant;
import java.time.LocalDate;

public record ReferenceTransaction(
        Long id,
        Long propertyId,
        ReferenceDealType dealType,
        LocalDate contractDate,
        Long price,
        Integer floorNo,
        ReferenceSource source,
        Instant cachedAt
) {
}
