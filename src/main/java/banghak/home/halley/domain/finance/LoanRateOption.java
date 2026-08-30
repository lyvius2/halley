package banghak.home.halley.domain.finance;

import java.math.BigDecimal;

/**
 * 대출 상품의 금리 옵션 한 줄 (설계 I77).
 *
 * <p>한 상품이 담보유형 × 상환방식 × 금리유형의 조합마다 다른 금리를 갖습니다. 그래서 상품이
 * 아니라 <b>이 옵션</b>이 금리를 비교하는 단위입니다.
 *
 * @param mortgageType 담보유형. <b>전세자금대출에는 없어 null</b>입니다
 * @param rateAvg      전월 취급 평균금리(%). 최저·최고는 조건부라 실제 감각에 가장 가까운 값이다
 */
public record LoanRateOption(
        String mortgageType,
        String mortgageTypeName,
        String repayType,
        String repayTypeName,
        String rateType,
        String rateTypeName,
        BigDecimal rateMin,
        BigDecimal rateMax,
        BigDecimal rateAvg
) {

    /** 비교에 쓸 대표 금리 — 평균이 없으면 최저로 대신한다. */
    public BigDecimal representativeRate() {
        return rateAvg != null ? rateAvg : rateMin;
    }
}
