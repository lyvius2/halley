package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/**
 * 전세자금대출 산정 결과 (설계 I67).
 *
 * <p>매매(주담대)와 필드가 다릅니다. <b>취득세·방공제·LTV·담보가치가 없습니다</b> —
 * 담보가 집이 아니라 보증기관의 보증이기 때문입니다.
 *
 * @param guaranteeLimit     보증 기준 한도 = min(보증금 × 보증비율, 보증기관 한도)
 * @param guaranteeRate      적용된 보증비율
 * @param guaranteeCap       적용된 보증기관 한도(원)
 * @param dsrLimit           DSR 기준 한도 — 전세대출은 <b>이자만</b> 반영한다
 * @param dsrCapacity        DSR 연간 상환 여력(원)
 * @param existingLoanAnnual 기존 대출의 연간 상환 추정액(원)
 * @param monthlyPayment     월 이자(원). 만기일시상환이라 원금은 매달 나가지 않는다
 * @param monthlyRate        월 이율 — 화면에서 슬라이더로 다시 계산할 때 쓴다
 */
public record JeonseEstimateResult(
        long guaranteeLimit,
        BigDecimal guaranteeRate,
        long guaranteeCap,
        long dsrLimit,
        long finalLimit,
        long requiredCash,
        long monthlyPayment,
        long dsrCapacity,
        long existingLoanAnnual,
        double monthlyRate,
        int termMonths
) {
}
