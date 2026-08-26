package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ScoredPropertyResponse(
        PropertyResponse property,
        BigDecimal totalScore,
        List<CriterionScoreView> scores
) {
}
