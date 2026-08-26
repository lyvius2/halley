package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

import java.util.Map;

public class CommuteScorer implements CriterionScorer {

    @Override
    public String code() {
        return "COMMUTE";
    }

    @Override
    public ScoringType type() {
        return ScoringType.AUTO;
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final Map<Long, Integer> commuteMinutes = ctx.commuteMinutes();
        if (commuteMinutes == null || commuteMinutes.isEmpty()) {
            return ScoreResult.missing("통근 데이터 없음");
        }
        double sum = 0.0;
        for (final int minutes : commuteMinutes.values()) {
            sum += Math.clamp(100.0 - (minutes - 20) * 1.43, 0.0, 100.0);
        }
        return ScoreResult.scored(sum / commuteMinutes.size());
    }
}
