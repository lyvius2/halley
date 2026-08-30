package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.scoring.ScoringType;

import java.math.BigDecimal;

/**
 * 채점 모달의 항목 한 줄 (설계 3.3 · I76).
 *
 * @param othersAverage 나를 뺀 다른 사용자들의 평균. `COMFORT`처럼 사용자마다 다르게 매기는
 *                      항목에서 "다른 사람은 어떻게 봤나"를 알려 주려는 값이다.
 *                      다른 평가자가 없으면 null
 * @param othersCount   그 평균을 만든 사람 수
 */
public record CriterionScoreView(
        String code,
        String name,
        ScoringType scoringType,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal effectiveScore,
        String scoreSource,
        String fallbackReason,
        String explanation,
        BigDecimal othersAverage,
        Integer othersCount
) {
}
