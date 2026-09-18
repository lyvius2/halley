package banghak.home.halley.domain.llm;

import java.math.BigDecimal;
import java.time.Instant;

public record ComparativeAnalysis(
        Long id,
        Long propertyId,
        Integer rankNo,
        BigDecimal score,
        String reason,
        String model,
        String batchHash,
        Integer propertyCount,
        Instant computedAt
) {
}
