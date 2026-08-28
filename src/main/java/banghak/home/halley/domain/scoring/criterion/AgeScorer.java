package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public class AgeScorer implements CriterionScorer {

    private static final double MIN_SCORE = 10.0;
    private static final double DEDUCTION_PER_YEAR = 2.5;

    @Override
    public String code() {
        return "AGE";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.approvalYear() == null) {
            return ScoreResult.missing("준공년도 없음");
        }
        final int age = ctx.referenceDate().getYear() - property.approvalYear();
        return ScoreResult.scored(Math.clamp(100.0 - age * DEDUCTION_PER_YEAR, MIN_SCORE, 100.0),
                String.format("%d년 준공 · 연식 %d년 → 100 − %d×%.1f (최저 %.0f점)",
                        property.approvalYear(), age, age, DEDUCTION_PER_YEAR, MIN_SCORE));
    }
}
