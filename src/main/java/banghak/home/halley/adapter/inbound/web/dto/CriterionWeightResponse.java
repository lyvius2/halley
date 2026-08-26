package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.scoring.ScoringType;

import java.math.BigDecimal;

public record CriterionWeightResponse(
        String criterionCode,
        String name,
        ScoringType scoringType,
        int priorityRank,
        BigDecimal weight
) {
}
