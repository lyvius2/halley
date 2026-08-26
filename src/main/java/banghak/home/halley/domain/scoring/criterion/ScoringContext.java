package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.loan.LoanCalculator;

import java.time.LocalDate;
import java.util.List;

public record ScoringContext(
        long cashBudget,
        List<Integer> comfortScores,
        LocalDate referenceDate,
        LoanCalculator loanCalculator
) {
}
