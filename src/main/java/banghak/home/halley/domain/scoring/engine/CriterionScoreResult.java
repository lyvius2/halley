package banghak.home.halley.domain.scoring.engine;

import java.math.BigDecimal;

public record CriterionScoreResult(
        String code,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal effectiveScore,
        String fallbackReason
) {
}
