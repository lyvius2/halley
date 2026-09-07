package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.scoring.ScoringType;

import java.math.BigDecimal;

/** 채점 모달의 항목 한 줄. */
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
