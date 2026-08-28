package banghak.home.halley.domain.loan;

/**
 * 대출 시뮬레이션 결과 (설계 3.4 · I55).
 *
 * @param dsrCapacity        DSR로 감당 가능한 연간 상환 총액(원) — 기존 대출 차감 전
 * @param existingLoanAnnual 기존 대출의 연간 상환 추정액(원) — DSR 여력에서 먼저 뺀다
 * @param monthlyRate        월 이율(스트레스 금리 포함) — 화면에서 슬라이더로 월 상환액을 다시 계산할 때 쓴다
 * @param termMonths         상환 기간(개월)
 */
public record LoanEstimateResult(
        long ltvLimit,
        long dsrLimit,
        long finalLimit,
        long requiredCash,
        long acquisitionTax,
        long monthlyPayment,
        long dsrCapacity,
        long existingLoanAnnual,
        double monthlyRate,
        int termMonths
) {
}
