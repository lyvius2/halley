package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/**
 * 전세자금대출 조건 (설계 I67).
 *
 * @param guaranteeRate 보증비율 — 보증기관이 보증금의 몇 %까지 보증하는지
 * @param guaranteeCap  보증기관 한도(원) — 비율과 무관한 절대 상한
 * @param interestRate  기준 금리
 * @param termYears     대출 기간(년). 전세 계약 주기와 같아 보통 2년이다
 */
public record JeonseTerms(
        BigDecimal guaranteeRate,
        long guaranteeCap,
        BigDecimal interestRate,
        int termYears
) {
}
