package banghak.home.halley.domain.loan;

/**
 * 기존 부채 한 건 (설계 I92).
 *
 * @param amount 잔액. 마이너스통장은 <b>쓴 금액이 아니라 한도</b>다
 */
public record ExistingDebt(DebtType type, long amount) {

    /**
     * DSR에 잡히는 연간 상환액.
     *
     * <p>종류가 정한 기간으로 원리금균등 상환한다고 보고 계산합니다. 이자만 보는 종류는
     * 원금을 빼고 이자만 셉니다(전세자금대출).
     *
     * @param annualRate 연이율(소수). 실제 대출 금리는 알 수 없어 신규 대출과 같다고 본다
     */
    public long annualPayment(double annualRate) {
        final long principal = Math.max(0L, amount);
        if (principal == 0L) {
            return 0L;
        }
        if (type.interestOnly()) {
            return (long) (principal * annualRate);
        }
        final double monthlyRate = annualRate / 12.0;
        final int months = type.dsrYears() * 12;
        if (monthlyRate == 0.0) {
            return principal / type.dsrYears();
        }
        final double monthly = principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months));
        return (long) (monthly * 12);
    }
}
