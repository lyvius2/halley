package banghak.home.halley.domain.loan;

/**
 * 전세자금대출 산정 입력 (설계 I67).
 *
 * @param deposit      전세보증금 또는 월세 보증금(원)
 * @param annualIncome 연소득(원)
 * @param cash         보유 현금(원)
 * @param existingLoan 기존 대출 잔액(원)
 */
public record JeonseEstimateInput(
        long deposit,
        long annualIncome,
        long cash,
        long existingLoan
) {
}
