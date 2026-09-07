package banghak.home.halley.domain.loan;

/** 전세자금대출 산정. */
public final class JeonseLoanCalculator {

    private final JeonseTerms terms;

    public JeonseLoanCalculator(JeonseTerms terms) {
        this.terms = terms;
    }

    public JeonseEstimateResult estimate(JeonseEstimateInput input, RegulationParams params) {
        final long deposit = Math.max(0L, input.deposit());
        final long byRate = (long) (deposit * terms.guaranteeRate().doubleValue());
        final long guaranteeLimit = Math.max(0L, Math.min(byRate, terms.guaranteeCap()));

        final double stressed = terms.interestRate().doubleValue()
                + params.effectiveStressRate(RateType.VARIABLE).doubleValue();
        final double annualRate = stressed;
        final double monthlyRate = terms.interestRate().doubleValue() / 12.0;
        final int months = Math.max(1, terms.termYears()) * 12;

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        final long existingLoanAnnual = existingLoanAnnual(input.existingLoan(), params);
        final long available = Math.max(0L, dsrCapacity - existingLoanAnnual);
        final long dsrLimit = annualRate <= 0.0 ? Long.MAX_VALUE : (long) (available / annualRate);

        final long finalLimit = Math.min(guaranteeLimit, dsrLimit);
        final long requiredCash = Math.max(0L, deposit - finalLimit);
        final long monthlyPayment = (long) (finalLimit * monthlyRate);

        return new JeonseEstimateResult(
                guaranteeLimit, terms.guaranteeRate(), terms.guaranteeCap(),
                dsrLimit, finalLimit, requiredCash, monthlyPayment,
                dsrCapacity, existingLoanAnnual, monthlyRate, months);
    }

 /** 기존 대출은 주담대 조건(원리금균등·프로파일 만기)으로 가정해 연간 상환액을 추정한다. */
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
