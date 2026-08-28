package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public class ComfortScorer implements CriterionScorer {

    @Override
    public String code() {
        return "COMFORT";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (ctx.comfortScores() == null || ctx.comfortScores().isEmpty()) {
            return ScoreResult.missing("사용자 평가 없음");
        }
        final double average = ctx.comfortScores().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        return ScoreResult.scored(average * 20.0);
    }
}
