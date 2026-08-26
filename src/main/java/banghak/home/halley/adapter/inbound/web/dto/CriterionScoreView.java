package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record CriterionScoreView(
        String code,
        String name,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal effectiveScore,
        String scoreSource,
        String fallbackReason
) {
}
