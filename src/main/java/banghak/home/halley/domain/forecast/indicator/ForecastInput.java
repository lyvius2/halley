package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.util.List;

/**
 * 지표 계산에 넣는 재료 (설계 I130).
 *
 * <p>지표마다 필요한 것이 달라 하나로 묶어 넘깁니다 — `ScoringContext`와 같은 방식입니다.
 * 아직은 실거래뿐이지만 전세가율·금리·용적률이 여기 붙습니다(구현 4).
 *
 * @param monthly <b>오래된 달부터</b> 정렬돼 있어야 합니다. 추세 계산이 순서를 전제합니다
 */
public record ForecastInput(
        Property property,
        List<MonthlyTrades> monthly
) {
}
