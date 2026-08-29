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
 * @param collateralValue    LTV의 기준이 된 담보가치(원) — 호가와 다를 수 있다
 * @param collateralSource   담보가치 출처. 신뢰도가 달라 화면에 함께 표기한다 (설계 I64-1)
 * @param leaseDeduction     LTV 한도에서 차감한 방공제(원). MCI 가입 시 0
 * @param monthlyRate        월 이율(스트레스 포함)
 * @param termMonths         상환 기간(개월)
 */
import banghak.home.halley.domain.loan.CollateralSource;

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
        Long collateralValue,
        CollateralSource collateralSource,
        String collateralSourceLabel,
        Integer collateralSampleCount,
        Boolean collateralReliable,
        Long leaseDeduction,
        Boolean mortgageInsured,
        Double monthlyRate,
        Integer termMonths
) {
}
