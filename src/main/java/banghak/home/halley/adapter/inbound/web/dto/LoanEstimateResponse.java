package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 대출 한도 계산 결과 (설계 3.4 · I55).
 *
 * <p>뒤쪽 필드들은 <b>화면의 대출액 슬라이더</b>가 매번 서버를 부르지 않고 월 상환액·필요 현금을
 * 다시 계산하기 위한 값입니다.
 *
 * @param askingPrice        매매가(원)
 * @param usedAnnualIncome   실제로 계산에 쓰인 연소득 — 프로필에서 채웠는지 확인용
 * @param dsrCapacity        DSR 연간 상환 여력(원)
 * @param existingLoanAnnual 기존 대출의 연간 상환 추정액(원)
 * @param monthlyRate        월 이율(스트레스 포함)
 * @param termMonths         상환 기간(개월)
 */
public record LoanEstimateResponse(
        Long propertyId,
        Long ltvLimit,
        Long dsrLimit,
        Long finalLimit,
        Long requiredCash,
        Long acquisitionTax,
        Long monthlyPayment,
        Long askingPrice,
        Long usedAnnualIncome,
        Long usedCash,
        Long usedExistingLoan,
        Long dsrCapacity,
        Long existingLoanAnnual,
        Double monthlyRate,
        Integer termMonths
) {
}
