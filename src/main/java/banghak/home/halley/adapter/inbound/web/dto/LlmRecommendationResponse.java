package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.LlmRecommendation;

import java.math.BigDecimal;
import java.time.Instant;

/** AI 추천도. */
public record LlmRecommendationResponse(
        Long propertyId,
        boolean pending,
        BigDecimal score,
        String reason,
        String model,
        Instant computedAt
) {

    public static LlmRecommendationResponse from(LlmRecommendation r) {
        return new LlmRecommendationResponse(
                r.propertyId(), false, r.score(), r.reason(), r.model(), r.computedAt());
    }

 /** 결과가 아직 없을 때. pending으로 '분석 중'과 '미산출'을 가른다. */
    public static LlmRecommendationResponse empty(Long propertyId, boolean pending) {
        return new LlmRecommendationResponse(propertyId, pending, null, null, null, null);
    }
}
