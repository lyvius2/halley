package banghak.home.halley.domain.loan;

/**
 * 대출 시뮬레이션 결과 (설계 3.4 · I55 · I64).
 *
 * @param dsrCapacity        DSR로 감당 가능한 연간 상환 총액(원) — 기존 대출 차감 전
 * @param existingLoanAnnual 기존 대출의 연간 상환 추정액(원) — DSR 여력에서 먼저 뺀다
 * @param collateralValue    LTV의 기준이 된 담보가치(원). 호가와 다를 수 있다
 * @param collateralSource   담보가치의 출처 — 신뢰도가 달라 화면에 함께 표기한다
 * @param collateralSampleCount 실거래로 매겼을 때 쓴 거래 건수 (설계 I65)
 * @param collateralReliable 신뢰할 만한 근거인지 — 호가·소표본이면 false
 * @param leaseDeduction     LTV 한도에서 차감한 방공제(원). MCI 가입 시 0
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
        long collateralValue,
        CollateralSource collateralSource,
        int collateralSampleCount,
        boolean collateralReliable,
        long leaseDeduction,
        double monthlyRate,
        int termMonths
) {
}
