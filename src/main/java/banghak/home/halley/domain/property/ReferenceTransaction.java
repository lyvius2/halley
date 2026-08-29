package banghak.home.halley.domain.property;

import java.time.Instant;
import java.time.LocalDate;

public record ReferenceTransaction(
        Long id,
        Long propertyId,
        ReferenceDealType dealType,
        LocalDate contractDate,
        Long price,
        java.math.BigDecimal areaM2,
        Integer floorNo,
        ReferenceSource source,
        Instant cachedAt
) {
}
