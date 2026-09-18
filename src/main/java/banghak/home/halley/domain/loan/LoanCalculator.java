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

    public LoanEstimateResult estimate(LoanEstimateInput input, RegulationParams params) {
        final long collateralValue = input.collateral().value();
        final long leaseDeduction = input.mortgageInsured() ? 0L : Math.max(0L, params.leaseDeduction());
        final long ltvBeforeCap = (long) (collateralValue * params.ltvRate().doubleValue()) - leaseDeduction;
        final long ltvLimit = Math.max(0L, Math.min(ltvBeforeCap, params.totalCap()));

        // 스트레스 금리는 한도를 역산할 때만 쓴다.
        // 실제로 내는 돈은 실금리 기준인데, 예전에는 월 상환액에도 섞여 있어 부풀려 보였다
        final double stressed =
                params.interestRate().doubleValue()
                        + params.effectiveStressRate(input.rateType()).doubleValue();
        final double monthlyRate = params.interestRate().doubleValue() / 12.0;
        final double dsrMonthlyRate = stressed / 12.0;
        final int months = params.termYears() * 12;
        final double annuityFactor = annuityFactor(dsrMonthlyRate, months);

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        // 부채 종류마다 DSR 산정만기가 다르다. 전부 30년 주담대로 보면
        // 신용대출·마이너스통장의 부담이 실제보다 훨씬 작게 잡혀 한도가 부풀려진다
        // 기존 부채의 DSR 부담도 스트레스 기준이다
        final long existingLoanAnnual = input.existingDebtAnnualPayment(stressed);
        final long available = Math.max(0L, dsrCapacity - existingLoanAnnual);
        final long dsrLimit = (long) (available / 12.0 * annuityFactor);

        final long finalLimit = Math.min(ltvLimit, dsrLimit);
        // 필요 현금·취득세는 실제로 지불하는 금액인 호가 기준이다 — 담보가치가 아니다
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

    /** 원리금균등 월 상환액.  */
    private double monthlyPaymentOf(long principal, double monthlyRate, int months) {
        if (monthlyRate == 0.0) {
            return (double) principal / months;
        }
        return principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months));
    }

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
