package banghak.home.halley.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;

/** 금융기관 확정 결과가 아닌 입력 기반 대출 추정 조건. */
public record BudgetFinancing(
        Long id,
        Long planId,
        long loanAmount,
        BigDecimal loanRatio,
        BigDecimal interestRate,
        int termMonths,
        RepaymentType repaymentType,
        long monthlyPayment,
        boolean estimate,
        Instant createdAt,
        Instant updatedAt
) {

    public BudgetFinancing {
        if (planId == null) {
            throw new IllegalArgumentException("planId is required");
        }
        if (loanAmount < 0 || interestRate == null || interestRate.signum() < 0
                || termMonths <= 0 || monthlyPayment < 0) {
            throw new IllegalArgumentException("financing values are invalid");
        }
        repaymentType = repaymentType == null ? RepaymentType.AMORTIZED : repaymentType;
    }
}
