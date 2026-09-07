package banghak.home.halley.domain.loan;

import java.util.List;

/** 대출 산정 입력. */
public record LoanEstimateInput(
        long askingPrice,
        CollateralValuation collateral,
        long annualIncome,
        long cash,
        long existingLoan,
        List<ExistingDebt> existingDebts,
        boolean firstHome,
        boolean mortgageInsured,
        RateType rateType
) {

    public LoanEstimateInput {
        existingDebts = existingDebts == null ? List.of() : List.copyOf(existingDebts);
        rateType = rateType == null ? RateType.VARIABLE : rateType;
    }

 /** DSR에 잡히는 기존 부채의 연간 상환액. */
    public long existingDebtAnnualPayment(double annualRate) {
        if (!existingDebts.isEmpty()) {
            return existingDebts.stream()
                    .mapToLong(debt -> debt.annualPayment(annualRate))
                    .sum();
        }
        return new ExistingDebt(DebtType.MORTGAGE, existingLoan).annualPayment(annualRate);
    }
}
