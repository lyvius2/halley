package banghak.home.halley.domain.llm;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LLM이 매긴 매물 추천도 (설계 I59).
 *
 * @param promptHash     입력(매물 정보 + 직장 위치)의 해시. 그대로면 다시 부르지 않아 비용을 아낀다
 * @param workplaceCount 추론에 쓰인 직장 위치 수. 3곳 이상이면 사용자가 늘어도 다시 묻지 않는다 (설계 I60)
 */
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
