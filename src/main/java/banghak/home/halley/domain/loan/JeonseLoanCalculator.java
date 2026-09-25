package banghak.home.halley.domain.loan;

public final class JeonseLoanCalculator {

    private final JeonseTerms terms;

    public JeonseLoanCalculator(JeonseTerms terms) {
        this.terms = terms;
    }

    public JeonseEstimateResult estimate(JeonseEstimateInput input, RegulationParams params) {
        final long deposit = Math.max(0L, input.deposit());
        final long byRate = (long) (deposit * terms.guaranteeRate().doubleValue());
        final long guaranteeLimit = Math.max(0L, Math.min(byRate, terms.guaranteeCap()));

        // 스트레스 금리는 한도를 역산할 때만 쓴다.
        // 전세대출은 이자만 내므로 실제 월 이자는 실금리 기준이다
        final double stressed = terms.interestRate().doubleValue()
                + params.effectiveStressRate(RateType.VARIABLE).doubleValue();
        final double annualRate = stressed;
        final double monthlyRate = terms.interestRate().doubleValue() / 12.0;
        final int months = Math.max(1, terms.termYears()) * 12;

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        // 기존 부채는 그 조건을 알 수 없어 주담대와 같은 방식으로 추정한다
        final long existingLoanAnnual = existingLoanAnnual(input.existingLoan(), params);
        final long available = Math.max(0L, dsrCapacity - existingLoanAnnual);
        // 이자만 내므로 원금 한도 = 연간 여력 ÷ 연 이율
        final long dsrLimit = annualRate <= 0.0 ? Long.MAX_VALUE : (long) (available / annualRate);

        final long finalLimit = Math.min(guaranteeLimit, dsrLimit);
        final long requiredCash = Math.max(0L, deposit - finalLimit);
        final long monthlyPayment = (long) (finalLimit * monthlyRate);

        return new JeonseEstimateResult(
                guaranteeLimit, terms.guaranteeRate(), terms.guaranteeCap(),
                dsrLimit, finalLimit, requiredCash, monthlyPayment,
                dsrCapacity, existingLoanAnnual, monthlyRate, months);
    }

    /** 기존 대출은 주담대 조건(원리금균등·프로파일 만기)으로 가정해 연간 상환액을 추정한다.  */
    private long existingLoanAnnual(long existingLoan, RegulationParams params) {
        final long principal = Math.max(0L, existingLoan);
        if (principal == 0L) {
            return 0L;
        }
        final double monthlyRate =
                (params.interestRate().doubleValue()
                        + params.effectiveStressRate(RateType.VARIABLE).doubleValue()) / 12.0;
        final int months = params.termYears() * 12;
        if (monthlyRate == 0.0) {
            return (long) ((double) principal / months * 12);
        }
        return (long) (principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months)) * 12);
    }
}
