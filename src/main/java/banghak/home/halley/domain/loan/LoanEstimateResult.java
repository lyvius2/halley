package banghak.home.halley.domain.loan;

public record LoanEstimateResult(
        long ltvLimit,
        long dsrLimit,
        long finalLimit,
        long requiredCash,
        long acquisitionTax,
        long monthlyPayment
) {
}
