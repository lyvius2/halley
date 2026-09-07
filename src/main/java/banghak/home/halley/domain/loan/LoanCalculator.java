package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

public final class LoanCalculator {

    private final BigDecimal ltvRate;
    private final long totalCap;

    public LoanCalculator(BigDecimal ltvRate, long totalCap) {
        this.ltvRate = ltvRate;
        this.totalCap = totalCap;
    }

    public long expectedLoanLimit(long askingPrice) {
        final long ltvLimit = (long) (askingPrice * ltvRate.doubleValue());
        return Math.min(ltvLimit, totalCap);
    }

 /** LTV/DSR 기반 자체 대출 시뮬레이션. */
    public LoanEstimateResult estimate(LoanEstimateInput input, RegulationParams params) {
        final long collateralValue = input.collateral().value();
        final long leaseDeduction = input.mortgageInsured() ? 0L : Math.max(0L, params.leaseDeduction());
        final long ltvBeforeCap = (long) (collateralValue * params.ltvRate().doubleValue()) - leaseDeduction;
        final long ltvLimit = Math.max(0L, Math.min(ltvBeforeCap, params.totalCap()));

        final double stressed =
                params.interestRate().doubleValue()
                        + params.effectiveStressRate(input.rateType()).doubleValue();
        final double monthlyRate = params.interestRate().doubleValue() / 12.0;
        final double dsrMonthlyRate = stressed / 12.0;
        final int months = params.termYears() * 12;
        final double annuityFactor = annuityFactor(dsrMonthlyRate, months);

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        final long existingLoanAnnual = input.existingDebtAnnualPayment(stressed);
        final long available = Math.max(0L, dsrCapacity - existingLoanAnnual);
        final long dsrLimit = (long) (available / 12.0 * annuityFactor);

        final long finalLimit = Math.min(ltvLimit, dsrLimit);
        final long requiredCash = Math.max(0L, input.askingPrice() - finalLimit);
        final long acquisitionTax = (long) (input.askingPrice() * acquisitionTaxRate(input.askingPrice())
                * (input.firstHome() ? (1 - params.firstHomeDiscount().doubleValue()) : 1.0));

        return new LoanEstimateResult(
                ltvLimit, dsrLimit, finalLimit, requiredCash, acquisitionTax,
                (long) monthlyPaymentOf(finalLimit, monthlyRate, months),
                dsrCapacity, existingLoanAnnual,
                collateralValue, input.collateral().source(),
                input.collateral().sampleCount(), input.collateral().isReliable(), leaseDeduction,
                monthlyRate, dsrMonthlyRate, months);
    }

    private double annuityFactor(double monthlyRate, int months) {
        return monthlyRate == 0.0 ? months : (1 - Math.pow(1 + monthlyRate, -months)) / monthlyRate;
    }

 /** 원리금균등 월 상환액. */
    private double monthlyPaymentOf(long principal, double monthlyRate, int months) {
        if (monthlyRate == 0.0) {
            return (double) principal / months;
        }
        return principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months));
    }

 /** 취득세율 구간: 6억 이하 1%, 6~9억 1→3% 구간, 9억 초과 3%. */
    private double acquisitionTaxRate(long price) {
        if (price <= 600_000_000L) {
            return 0.01;
        }
        if (price <= 900_000_000L) {
            return 0.01 + 0.02 * (price - 600_000_000L) / 300_000_000L;
        }
        return 0.03;
    }
}
