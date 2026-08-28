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
     *
     * <p><b>기존 대출이 있으면 그 연간 상환액을 먼저 뺀다</b>(설계 I55). DSR은 <em>모든</em> 대출의
     * 원리금을 합쳐 보는 규제라, 기존 대출을 무시하면 한도가 실제보다 높게 나온다. 기존 대출의 상환액은
     * 조건을 알 수 없으므로 신규 대출과 같은 금리·기간으로 가정해 추정한다.
     */
    public LoanEstimateResult estimate(long askingPrice, long annualIncome, long cash, boolean firstHome,
                                       RegulationParams params) {
        return estimate(askingPrice, annualIncome, cash, 0L, firstHome, params);
    }

    public LoanEstimateResult estimate(long askingPrice, long annualIncome, long cash, long existingLoan,
                                       boolean firstHome, RegulationParams params) {
        final long ltvLimit = Math.min((long) (askingPrice * params.ltvRate().doubleValue()), params.totalCap());

        final double monthlyRate = (params.interestRate().doubleValue() + params.stressRate().doubleValue()) / 12.0;
        final int months = params.termYears() * 12;
        final double annuityFactor = annuityFactor(monthlyRate, months);

        final long dsrCapacity = (long) (annualIncome * params.dsrRatio().doubleValue());
        final long existingLoanAnnual = Math.max(0L, existingLoan) == 0L
                ? 0L
                : (long) (monthlyPaymentOf(Math.max(0L, existingLoan), monthlyRate, months) * 12);
        final long available = Math.max(0L, dsrCapacity - existingLoanAnnual);
        final long dsrLimit = (long) (available / 12.0 * annuityFactor);

        final long finalLimit = Math.min(ltvLimit, dsrLimit);
        final long requiredCash = Math.max(0L, askingPrice - finalLimit);
        final long acquisitionTax = (long) (askingPrice * acquisitionTaxRate(askingPrice)
                * (firstHome ? (1 - params.firstHomeDiscount().doubleValue()) : 1.0));

        return new LoanEstimateResult(
                ltvLimit, dsrLimit, finalLimit, requiredCash, acquisitionTax,
                (long) monthlyPaymentOf(finalLimit, monthlyRate, months),
                dsrCapacity, existingLoanAnnual, monthlyRate, months);
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
