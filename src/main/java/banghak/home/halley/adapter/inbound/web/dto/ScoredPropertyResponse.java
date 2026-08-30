package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 채점까지 붙은 매물 하나.
 *
 * @param scoreVersion 채점이 바뀔 때마다 오르는 번호 (설계 I85). 화면은 이 값을 들고 있다가
 *                     서버 값과 달라지면 다시 받는다 — 뒤에서 채점이 끝난 것을 알아채는 신호다
 */
public record ScoredPropertyResponse(
        PropertyResponse property,
        BigDecimal totalScore,
        List<CriterionScoreView> scores,
        long scoreVersion
) {
}
