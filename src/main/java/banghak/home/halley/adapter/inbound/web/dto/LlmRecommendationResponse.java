package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.LlmRecommendation;

import java.math.BigDecimal;
import java.time.Instant;

/** AI 추천도 (설계 I59). */
public record LlmRecommendationResponse(
        Long propertyId,
        BigDecimal score,
        String reason,
        String model,
        Instant computedAt
) {

    public static LlmRecommendationResponse from(LlmRecommendation r) {
        return new LlmRecommendationResponse(r.propertyId(), r.score(), r.reason(), r.model(), r.computedAt());
    }
}
