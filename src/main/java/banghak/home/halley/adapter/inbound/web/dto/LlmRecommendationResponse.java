package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.llm.LlmRecommendation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 추천도 (설계 I59 · I72).
 *
 * <p>폴링이 두드리는 응답이라 <b>결과가 없어도 200</b>으로 돌려줍니다. 본문 없이 204를 주면
 * 화면이 "분석 중"과 "미산출"을 구분할 수 없습니다.
 *
 * @param pending 아직 응답을 기다리는 중인지. 이 값이 true일 때만 화면에 진행 표시를 띄운다
 */
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

    /** 결과가 아직 없을 때. `pending`으로 '분석 중'과 '미산출'을 가른다. */
    public static LlmRecommendationResponse empty(Long propertyId, boolean pending) {
        return new LlmRecommendationResponse(propertyId, pending, null, null, null, null);
    }
}
