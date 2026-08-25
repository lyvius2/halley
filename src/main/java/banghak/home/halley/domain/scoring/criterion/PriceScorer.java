package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

public class PriceScorer implements CriterionScorer {

    @Override
    public String code() {
        return "PRICE";
    }

    @Override
    public ScoringType type() {
        return ScoringType.AUTO;
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final Long askingPrice = property.priceDeposit();
        if (askingPrice == null || askingPrice <= 0) {
            return ScoreResult.missing("호가 없음");
        }
        final long budget = ctx.cashBudget() + ctx.loanCalculator().expectedLoanLimit(askingPrice);
        if (budget <= 0) {
            return ScoreResult.scored(0.0);
        }
        final double targetValue = 100.0 * (1.0 - (double) askingPrice / budget);
        return ScoreResult.scored(Math.clamp(targetValue, 0.0, 100.0));
    }
}
