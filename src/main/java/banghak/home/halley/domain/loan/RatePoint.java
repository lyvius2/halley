package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 한 시점의 금리 (설계 I116).
 *
 * @param rate <b>소수</b>다. ECOS는 `"5.04"`처럼 퍼센트로 주므로(`UNIT_NAME: 연%`)
 *             어댑터에서 100으로 나눠 담는다. 여기서 단위를 통일해 두지 않으면
 *             계산 어딘가에서 100배 어긋난다
 */
public record RatePoint(YearMonth month, BigDecimal rate) {
}
