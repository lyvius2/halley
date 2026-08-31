package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 채점까지 붙은 매물 하나.
 *
 * @param scoreVersion 채점이 바뀔 때마다 오르는 번호 (설계 I85). 화면은 이 값을 들고 있다가
 *                     서버 값과 달라지면 다시 받는다 — 뒤에서 채점이 끝난 것을 알아채는 신호다
 * @param forecast     가격 전망 <b>요약</b> (설계 I136). 요인 상세는 모달에서 따로 받는다.
 *                     아직 안 냈으면 null
 */
public record ScoredPropertyResponse(
        PropertyResponse property,
        BigDecimal totalScore,
        List<CriterionScoreView> scores,
        long scoreVersion,
        ForecastSummary forecast
) {

    /** 전망만 갈아 끼운다 — 채점 경로가 전망을 몰라도 되도록. */
    public ScoredPropertyResponse withForecast(ForecastSummary summary) {
        return new ScoredPropertyResponse(property, totalScore, scores, scoreVersion, summary);
    }
}
