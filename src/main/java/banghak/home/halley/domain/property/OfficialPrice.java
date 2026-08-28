package banghak.home.halley.domain.property;

import java.math.BigDecimal;

/**
 * 공시가격 한 건 (설계 I54).
 *
 * @param price     공시가격(원)
 * @param year      기준연도
 * @param dongName  동명 — 공동주택은 같은 PNU 아래 동·호별로 여러 건이 나온다
 * @param hoName    호명
 * @param areaM2    전용면적(㎡) — 매물 면적과 맞춰 고를 때 쓴다
 */
public record OfficialPrice(
        Long price,
        Integer year,
        String dongName,
        String hoName,
        BigDecimal areaM2
) {
}
