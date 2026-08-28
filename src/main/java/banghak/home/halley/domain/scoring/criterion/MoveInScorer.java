package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;

import java.time.temporal.ChronoUnit;

public class MoveInScorer implements CriterionScorer {

    private static final long REFERENCE_DAYS = 90L;
    private static final double DEDUCTION_PER_DAY = 40.0 / REFERENCE_DAYS;

    @Override
    public String code() {
        return "MOVE_IN";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.moveInType() == null) {
            return ScoreResult.missing("입주시기 없음");
        }
        return switch (property.moveInType()) {
            case IMMEDIATE -> ScoreResult.scored(100.0);
            case NEGOTIABLE -> ScoreResult.scored(85.0);
            case DATE -> scoreByDate(property, ctx);
        };
    }

    private ScoreResult scoreByDate(Property property, ScoringContext ctx) {
        if (property.moveInDate() == null) {
            return ScoreResult.missing("입주가능일 없음");
        }
        final long days = ChronoUnit.DAYS.between(ctx.referenceDate(), property.moveInDate());
        if (days < 0) {
            return ScoreResult.scored(100.0);
        }
        if (days > REFERENCE_DAYS) {
            return ScoreResult.scored(0.0);
        }
        return ScoreResult.scored(100.0 - days * DEDUCTION_PER_DAY);
    }
}
