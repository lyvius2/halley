package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

public record LtvDecision(BigDecimal rate, long cap, RegulationZone zone, String reason) {
}
