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

    /**
     * LTV/DSR 기반 자체 대출 시뮬레이션 (설계 3.4). DSR은 연 소득 × DSR비율의 연간 상환 상한을
     * 연금(annuity) 공식으로 원금 한도로 환산한다.
     */
    public LoanEstimateResult estimate(long askingPrice, long annualIncome, long cash, boolean firstHome,
                                       RegulationParams params) {
        final long ltvLimit = Math.min((long) (askingPrice * params.ltvRate().doubleValue()), params.totalCap());

        final double monthlyRate = (params.interestRate().doubleValue() + params.stressRate().doubleValue()) / 12.0;
        final int months = params.termYears() * 12;
        final long annualDebtCap = (long) (annualIncome * params.dsrRatio().doubleValue());
        final double maxMonthlyPayment = annualDebtCap / 12.0;
        final double annuityFactor = monthlyRate == 0.0
                ? months
                : (1 - Math.pow(1 + monthlyRate, -months)) / monthlyRate;
        final long dsrLimit = (long) (maxMonthlyPayment * annuityFactor);

        final long finalLimit = Math.min(ltvLimit, dsrLimit);
        final long requiredCash = Math.max(0L, askingPrice - finalLimit);
        final long acquisitionTax = (long) (askingPrice * acquisitionTaxRate(askingPrice)
                * (firstHome ? (1 - params.firstHomeDiscount().doubleValue()) : 1.0));

        final double monthlyPayment = monthlyRate == 0.0
                ? (double) finalLimit / months
                : finalLimit * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months));

        return new LoanEstimateResult(
                ltvLimit, dsrLimit, finalLimit, requiredCash, acquisitionTax, (long) monthlyPayment);
    }

    /**
     * 취득세율 구간: 6억 이하 1%, 6~9억 1→3% 구간, 9억 초과 3%.
     */
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
