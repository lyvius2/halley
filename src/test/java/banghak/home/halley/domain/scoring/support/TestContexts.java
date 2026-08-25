package banghak.home.halley.domain.scoring.support;

import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.scoring.criterion.ScoringContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TestContexts {

    public static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 26);

    private TestContexts() {
    }

    public static LoanCalculator loanCalculator() {
        return new LoanCalculator(new BigDecimal("0.4"), 1_000_000_000L);
    }

    public static ScoringContext context() {
        return new ScoringContext(500_000_000L, List.of(), REFERENCE_DATE, loanCalculator());
    }

    public static ScoringContext context(long cashBudget, List<Integer> comfortScores) {
        return new ScoringContext(cashBudget, comfortScores, REFERENCE_DATE, loanCalculator());
    }
}
