package banghak.home.halley.domain.loan;

/**
 * 전세자금대출 산정 (설계 I67).
 *
 * <p>주담대와 <b>계산 구조가 다릅니다.</b> 담보가 집이 아니라 보증기관(HUG·HF·SGI)의 보증이라
 * LTV·담보가치·방공제가 없고, 소유권이 넘어오지 않으므로 <b>취득세도 없습니다.</b>
 *
 * <pre>
 * 보증 한도 = min(보증금 × 보증비율, 보증기관 한도)
 * DSR 한도  = (연간 여력 ÷ 스트레스 금리)        ← 이자만
 * 최종 한도 = min(보증 한도, DSR 한도)
 * 필요 현금 = 보증금 − 최종 한도
 * </pre>
 *
 * <p><b>DSR에 원금이 들어가지 않습니다.</b> 전세대출은 만기일시상환이라 매달 이자만 냅니다.
 * 주담대처럼 연금 공식으로 원금까지 상환한다고 보면 한도가 실제보다 훨씬 낮게 나옵니다.
 */
public final class JeonseLoanCalculator {

    private final JeonseTerms terms;

    public JeonseLoanCalculator(JeonseTerms terms) {
        this.terms = terms;
    }

    public JeonseEstimateResult estimate(JeonseEstimateInput input, RegulationParams params) {
        final long deposit = Math.max(0L, input.deposit());
        final long byRate = (long) (deposit * terms.guaranteeRate().doubleValue());
        final long guaranteeLimit = Math.max(0L, Math.min(byRate, terms.guaranteeCap()));

        // 스트레스 금리는 전세대출 DSR에도 얹는다 (설계 I64-2와 같은 이유)
        final double annualRate = terms.interestRate().doubleValue() + params.stressRate().doubleValue();
        final double monthlyRate = annualRate / 12.0;
        final int months = Math.max(1, terms.termYears()) * 12;

        final long dsrCapacity = (long) (input.annualIncome() * params.dsrRatio().doubleValue());
        // 기존 부채는 그 조건을 알 수 없어 주담대와 같은 방식으로 추정한다 (설계 I55)
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

    /** 기존 대출은 주담대 조건(원리금균등·프로파일 만기)으로 가정해 연간 상환액을 추정한다. */
    private long existingLoanAnnual(long existingLoan, RegulationParams params) {
        final long principal = Math.max(0L, existingLoan);
        if (principal == 0L) {
            return 0L;
        }
        final double monthlyRate =
                (params.interestRate().doubleValue() + params.stressRate().doubleValue()) / 12.0;
        final int months = params.termYears() * 12;
        if (monthlyRate == 0.0) {
            return (long) ((double) principal / months * 12);
        }
        return (long) (principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months)) * 12);
    }
}
