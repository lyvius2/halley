package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.scoring.ScoringType;

import java.math.BigDecimal;

public record CriterionScoreView(
        String code,
        String name,
        ScoringType scoringType,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal effectiveScore,
        String scoreSource,
        String fallbackReason,
        String explanation
) {
}
