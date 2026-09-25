package banghak.home.halley.domain.property;

import java.math.BigDecimal;

public record OfficialPrice(
        Long price,
        Integer year,
        String dongName,
        String hoName,
        BigDecimal areaM2
) {
}
