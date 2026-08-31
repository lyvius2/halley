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
 * @param myScore       <b>내가</b> 매긴 점수 (설계 I118). `COMFORT`는 사람마다 따로 매기는데
 *                      응답에는 그룹 평균만 실려 있어, 남이 매기면 <b>나도 매긴 것처럼</b>
 *                      보였다. 아직 안 매겼으면 null
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
        Integer othersCount,
        Integer myScore
) {
}
