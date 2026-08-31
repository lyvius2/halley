package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.loan.RatePoint;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.util.List;

/**
 * 지표 계산에 넣는 재료 (설계 I130).
 *
 * <p>지표마다 필요한 것이 달라 하나로 묶어 넘깁니다 — `ScoringContext`와 같은 방식입니다.
 *
 * <p><b>전부 코드가 이미 구해 둔 값입니다.</b> 지표는 계산만 하고, 외부를 부르지 않습니다.
 *
 * @param monthlyTrades 매매. <b>오래된 달부터</b> 정렬돼 있어야 합니다 — 추세 계산이 순서를 전제합니다
 * @param monthlyJeonse 순수 전세. 금액은 <b>보증금</b>입니다 (설계 I131)
 * @param loanRates     가계대출 금리 시계열 (ECOS, 설계 I116). 정렬은 보장하지 않는다
 * @param landUses      토지이용계획 (설계 I69). 용도지역을 여기서 고른다
 */
public record ForecastInput(
        Property property,
        List<MonthlyTrades> monthlyTrades,
        List<MonthlyTrades> monthlyJeonse,
        List<RatePoint> loanRates,
        List<LandUse> landUses
) {

    /** 실거래만 있으면 되는 지표를 위한 간편 생성 — 테스트와 초기 단계에서 쓴다. */
    public static ForecastInput ofTrades(Property property, List<MonthlyTrades> monthlyTrades) {
        return new ForecastInput(property, monthlyTrades, List.of(), List.of(), List.of());
    }
}
