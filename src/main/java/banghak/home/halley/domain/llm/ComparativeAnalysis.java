package banghak.home.halley.domain.llm;

import java.math.BigDecimal;
import java.time.Instant;

/** 비교 우위 분석 결과 한 건. */
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
