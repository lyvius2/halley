package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/** LTV 비율 판정 결과. */
public record LtvDecision(BigDecimal rate, long cap, RegulationZone zone, String reason) {
}
