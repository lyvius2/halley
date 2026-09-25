package banghak.home.halley.domain.finance;

import java.math.BigDecimal;

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

    /** 비교에 쓸 대표 금리 — 평균이 없으면 최저로 대신한다.  */
    public BigDecimal representativeRate() {
        return rateAvg != null ? rateAvg : rateMin;
    }
}
