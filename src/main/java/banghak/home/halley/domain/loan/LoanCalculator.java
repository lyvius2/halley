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
     * LTV/DSR 기반 자체 대출 시뮬레이션 (설계 3.4 · I64).
     *
     * <p><b>LTV는 호가가 아니라 담보가치에 매깁니다.</b> 은행이 보는 값은 KB시세이고, 호가는
     * 파는 쪽이 부른 값이라 담보가치보다 높기 쉽습니다. 실측에서 1.5억까지 벌어졌습니다(설계 9.2).
     *
     * <p><b>방공제를 LTV 한도에서 뺍니다.</b> 소액임차보증금 최우선변제금은 선순위라 은행이
     * 그만큼 덜 빌려줍니다. 빼먹으면 수천만 원 단위로 높게 나옵니다. MCI/MCG에 가입하면 면제됩니다.
     *
     * <p>DSR은 연 소득 × DSR비율의 연간 상환 상한을 연금(annuity) 공식으로 원금 한도로 환산하며,
     * <b>기존 대출이 있으면 그 연간 상환액을 먼저 뺍니다</b>(설계 I55). DSR은 모든 대출의 원리금을
     * 합쳐 보는 규제라 무시하면 한도가 실제보다 높게 나옵니다. 기존 대출의 조건은 알 수 없으므로
     * 신규 대출과 같은 금리·기간으로 가정해 추정합니다.
     *
     * <p>금리는 <b>스트레스 금리를 더한 값</b>으로 계산합니다. 실제 대출 금리로 DSR을 역산하면
     * 한도가 부풀려집니다(설계 I64-2).
     */
    public LoanEstimateResult estimate(LoanEstimateInput input, RegulationParams params) {
        final long collateralValue = input.collateral().value();
        final long leaseDeduction = input.mortgageInsured() ? 0L : Math.max(0L, params.leaseDeduction());
        final long ltvBeforeCap = (long) (collateralValue * params.ltvRate().doubleValue()) - leaseDeduction;
        final long ltvLimit = Math.max(0L, Math.min(ltvBeforeCap, params.totalCap()));

        final double monthlyRate = (params.interestRate().doubleValue() + params.stressRate().doubleValue()) / 12.0;
        final int months = params.termYears() * 12;
        final double annuityFactor = annuityFactor(monthlyRate, months);

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        // 부채 종류마다 DSR 산정만기가 다르다 (설계 I92). 전부 30년 주담대로 보면
        // 신용대출·마이너스통장의 부담이 실제보다 훨씬 작게 잡혀 한도가 부풀려진다
        final long existingLoanAnnual = input.existingDebtAnnualPayment(monthlyRate * 12.0);
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
                monthlyRate, months);
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
