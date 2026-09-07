package banghak.home.halley.domain.finance;

import java.math.BigDecimal;

/** 시장에서 실제로 취급된 대표 금리. */
public record MarketRate(
        BigDecimal rate,
        LoanProductType type,
        String rateTypeName,
        int sampleCount,
        String dclsMonth
) {

 /** 화면에 그대로 띄울 한 줄. 왜 이 금리인지 설명한다. */
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
