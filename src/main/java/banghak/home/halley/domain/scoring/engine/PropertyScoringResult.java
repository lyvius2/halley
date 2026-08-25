package banghak.home.halley.domain.scoring.engine;

import java.math.BigDecimal;
import java.util.List;

public record PropertyScoringResult(
        List<CriterionScoreResult> criteria,
        BigDecimal totalScore
) {
}
