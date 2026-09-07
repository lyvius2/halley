package banghak.home.halley.domain.loan;

/** 전세자금대출 산정 입력. */
public record JeonseEstimateInput(
        long deposit,
        long annualIncome,
        long cash,
        long existingLoan
) {
}
