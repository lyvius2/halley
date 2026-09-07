package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/** 대출 산정에 쓰는 규제 수치. */
public record RegulationParams(
        BigDecimal ltvRate,
        long totalCap,
        BigDecimal dsrRatio,
        BigDecimal interestRate,
        BigDecimal stressRate,
        int termYears,
        BigDecimal acquisitionTaxRate,
        BigDecimal firstHomeDiscount,
        long leaseDeduction,
        BigDecimal officialPriceRatio,
        BigDecimal stressApplyRatio
) {

 /** 실제로 더할 스트레스 금리. */
    public BigDecimal effectiveStressRate(RateType rateType) {
        final BigDecimal ratio = stressApplyRatio == null ? BigDecimal.ONE : stressApplyRatio;
        final RateType type = rateType == null ? RateType.VARIABLE : rateType;
        return stressRate.multiply(ratio).multiply(type.weight());
    }

    public static RegulationParams defaults() {
        return new RegulationParams(
                new BigDecimal("0.4"), 990_000_000L, new BigDecimal("0.4"),
                new BigDecimal("0.04"), new BigDecimal("0.01"), 30,
                new BigDecimal("0.01"), new BigDecimal("0.5"),
                55_000_000L, new BigDecimal("0.7"), BigDecimal.ONE);
    }
}
