package banghak.home.halley.domain.llm;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 비교 우위 분석 결과 한 건 (설계 I61).
 *
 * <p>개별 매물을 따로 보는 AI 추천도(I59)와 달리, <b>등록된 매물 전체를 한 번에</b> 던져
 * 서로 견주게 한 결과입니다. 그래서 `reason`에는 "다른 매물 대비" 무엇이 낫고 못한지가 담깁니다.
 *
 * @param rankNo        1위가 가장 좋은 매물
 * @param batchHash     분석에 쓰인 매물 집합의 해시. 집합이 그대로면 다시 부르지 않는다
 * @param propertyCount 함께 비교한 매물 수 — 몇 개 중의 몇 위인지 알아야 순위가 뜻을 갖는다
 */
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
