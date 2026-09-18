package banghak.home.halley.domain.llm;

import java.math.BigDecimal;
import java.time.Instant;

public record LlmRecommendation(
        Long id,
        Long propertyId,
        BigDecimal score,
        String reason,
        String model,
        String promptHash,
        Integer workplaceCount,
        Instant computedAt
) {
}
