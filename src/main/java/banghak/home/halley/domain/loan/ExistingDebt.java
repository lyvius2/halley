package banghak.home.halley.domain.loan;

/** 기존 부채 한 건. */
public record ExistingDebt(DebtType type, long amount) {

 /** DSR에 잡히는 연간 상환액. */
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
