package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/**
 * LTV 비율 판정 결과 (설계 I66).
 *
 * @param rate   적용 LTV 비율 (0.0 ~ 1.0)
 * @param cap    이 조건에서의 대출 총액 상한(원)
 * @param zone   판정에 쓴 규제지역 구분
 * @param reason 왜 이 비율인지 — 화면에 그대로 보여준다. 숫자만 있으면 납득할 수 없다
 */
public record LtvDecision(BigDecimal rate, long cap, RegulationZone zone, String reason) {
}
