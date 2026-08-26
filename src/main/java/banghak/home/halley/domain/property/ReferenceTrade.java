package banghak.home.halley.domain.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReferenceTrade(
        String apartmentName,
        Long dealAmount,
        BigDecimal areaM2,
        Integer floorNo,
        LocalDate contractDate
) {
}
