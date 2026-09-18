package banghak.home.halley.domain.loan;

public record JeonseEstimateInput(
        long deposit,
        long annualIncome,
        long cash,
        long existingLoan
) {
}
