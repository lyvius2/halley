package banghak.home.halley.domain.finance;

import java.math.BigDecimal;

/**
 * 시장에서 실제로 취급된 대표 금리 (설계 I81).
 *
 * <p>지금까지 금리는 관리 화면에서 손으로 넣은 상수 하나였습니다. 이 값이 그것을 대체합니다.
 *
 * <p><b>어디서 왔는지 함께 담습니다.</b> 담보가치에 출처를 붙인 것(I65)과 같은 이유입니다 —
 * 사용자가 "3.62%가 어디서 나온 숫자인가"를 물을 수 있어야 하고, 못 받아 기본값으로 떨어졌을
 * 때 그 사실이 드러나야 합니다.
 *
 * @param rate       연이율. `0.0362`처럼 소수로 담는다 — 계산에 바로 쓰기 위해서다
 * @param dclsMonth  공시 제출월 `YYYYMM`. 금리는 월 단위로 갱신되므로 언제 값인지가 중요하다
 * @param sampleCount 이 값을 만든 옵션 수. 적으면 대표성이 떨어진다
 */
public record MarketRate(
        BigDecimal rate,
        LoanProductType type,
        String rateTypeName,
        int sampleCount,
        String dclsMonth
) {

    /** 화면에 그대로 띄울 한 줄 — 왜 이 금리인지 설명한다. */
    public String describe() {
        return String.format("은행 %d개 상품 %s 중앙값 (%s 공시)",
                sampleCount, rateTypeName == null ? "금리" : rateTypeName, formatMonth());
    }

    private String formatMonth() {
        if (dclsMonth == null || dclsMonth.length() != 6) {
            return "기준월 미상";
        }
        return dclsMonth.substring(0, 4) + "년 " + Integer.parseInt(dclsMonth.substring(4)) + "월";
    }
}
