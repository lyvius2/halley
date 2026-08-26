package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

public record RegulationParams(
        BigDecimal ltvRate,
        long totalCap,
        BigDecimal dsrRatio,
        BigDecimal interestRate,
        BigDecimal stressRate,
        int termYears,
        BigDecimal acquisitionTaxRate,
        BigDecimal firstHomeDiscount
) {

    public static RegulationParams defaults() {
        return new RegulationParams(
                new BigDecimal("0.4"), 990_000_000L, new BigDecimal("0.4"),
                new BigDecimal("0.04"), new BigDecimal("0.01"), 30,
                new BigDecimal("0.01"), new BigDecimal("0.5"));
    }
}
