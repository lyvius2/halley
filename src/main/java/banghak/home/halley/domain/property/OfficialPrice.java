package banghak.home.halley.domain.property;

import java.math.BigDecimal;

/** 공시가격 한 건. */
public record OfficialPrice(
        Long price,
        Integer year,
        String dongName,
        String hoName,
        BigDecimal areaM2
) {
}
