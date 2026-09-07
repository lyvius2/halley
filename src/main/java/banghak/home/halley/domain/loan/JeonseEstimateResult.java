package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/** 전세자금대출 산정 결과. */
public record JeonseEstimateResult(
        long guaranteeLimit,
        BigDecimal guaranteeRate,
        long guaranteeCap,
        long dsrLimit,
        long finalLimit,
        long requiredCash,
        long monthlyPayment,
        long dsrCapacity,
        long existingLoanAnnual,
        double monthlyRate,
        int termMonths
) {
}
