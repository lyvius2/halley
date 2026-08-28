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
        return ScoreResult.scored(average * 20.0,
                String.format("사용자 %d명 평가 평균 %.1f점(5점 만점) × 20", ctx.comfortScores().size(), average));
    }
}
