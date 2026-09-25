package banghak.home.halley.domain.loan;

public record LoanEstimateResult(
        long ltvLimit,
        long dsrLimit,
        long finalLimit,
        long requiredCash,
        long acquisitionTax,
        long monthlyPayment,
        long dsrCapacity,
        long existingLoanAnnual,
        long collateralValue,
        CollateralSource collateralSource,
        int collateralSampleCount,
        boolean collateralReliable,
        long leaseDeduction,
        double monthlyRate,
        /** DSR 한도를 역산할 때 쓴 월 이율. 실금리 + 실효 스트레스  */
        double dsrMonthlyRate,
        int termMonths
) {
}
