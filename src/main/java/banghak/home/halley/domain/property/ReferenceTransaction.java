package banghak.home.halley.domain.property;

import java.time.Instant;
import java.time.LocalDate;

public record ReferenceTransaction(
        Long id,
        /** 매물이 아니라 단지에 붙는다. */
        Long complexId,
        ReferenceDealType dealType,
        LocalDate contractDate,
        Long price,
        java.math.BigDecimal areaM2,
        Integer floorNo,
        ReferenceSource source,
        Instant cachedAt
) {
}
