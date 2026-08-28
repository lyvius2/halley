package banghak.home.halley.domain.scoring;

import java.math.BigDecimal;
import java.time.Instant;

public record PropertyScore(
        Long id,
        Long propertyId,
        String criterionCode,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal effectiveScore,
        ScoreSource scoreSource,
        String fallbackReason,
        String explanation,
        Instant computedAt
) {
}
