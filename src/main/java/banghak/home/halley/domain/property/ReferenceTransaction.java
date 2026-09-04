package banghak.home.halley.domain.property;

import java.time.Instant;
import java.time.LocalDate;

public record ReferenceTransaction(
        Long id,
        /** 매물이 아니라 <b>단지</b>에 붙는다 (설계 I266). */
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
