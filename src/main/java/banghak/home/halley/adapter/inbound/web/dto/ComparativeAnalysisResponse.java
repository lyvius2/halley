package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.ComparativeAnalysis;

import java.math.BigDecimal;
import java.time.Instant;

/** 비교 우위 분석 결과 한 건 (설계 I61). */
public record ComparativeAnalysisResponse(
        Long propertyId,
        String propertyName,
        Integer rank,
        Integer propertyCount,
        BigDecimal score,
        String reason,
        String model,
        Instant computedAt
) {

    public static ComparativeAnalysisResponse from(ComparativeAnalysis a, String propertyName) {
        return new ComparativeAnalysisResponse(
                a.propertyId(), propertyName, a.rankNo(), a.propertyCount(),
                a.score(), a.reason(), a.model(), a.computedAt());
    }
}
