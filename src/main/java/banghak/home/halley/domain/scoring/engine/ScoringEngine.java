package banghak.home.halley.domain.scoring.engine;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.criterion.CriterionScorer;
import banghak.home.halley.domain.scoring.criterion.ScoreResult;
import banghak.home.halley.domain.scoring.criterion.ScoringContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScoringEngine {

    public PropertyScoringResult score(Property property,
                                       ScoringContext ctx,
                                       List<CriterionScorer> scorers,
                                       Map<String, BigDecimal> weights,
                                       Map<String, BigDecimal> manualScores) {
        final List<CriterionScoreResult> criteria = new ArrayList<>();
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (final CriterionScorer scorer : scorers) {
            final ScoreResult result = scorer.score(property, ctx);
            final BigDecimal manual = manualScores == null ? null : manualScores.get(scorer.code());
            final BigDecimal effective = manual != null ? manual : result.score();
            if (effective != null) {
                final double weight = weightOf(scorer.code(), weights);
                weightedSum += effective.doubleValue() * weight;
                totalWeight += weight;
            }
            criteria.add(new CriterionScoreResult(
                    scorer.code(), result.score(), manual, effective,
                    result.fallbackReason(), result.explanation()));
        }
        final BigDecimal total = totalWeight > 0.0
                ? BigDecimal.valueOf(weightedSum / totalWeight).setScale(2, RoundingMode.HALF_UP)
                : null;
        return new PropertyScoringResult(List.copyOf(criteria), total);
    }

    private double weightOf(String code, Map<String, BigDecimal> weights) {
        if (weights == null) {
            return 0.0;
        }
        final BigDecimal weight = weights.get(code);
        return weight == null ? 0.0 : weight.doubleValue();
    }
}
