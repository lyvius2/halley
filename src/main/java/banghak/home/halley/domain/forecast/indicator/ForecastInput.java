package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.building.BuildingLedger;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.loan.RatePoint;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.time.YearMonth;
import java.util.List;

/**
 * 지표 계산에 넣는 재료.
 *
 * 지표마다 필요한 것이 달라 하나로 묶어 넘깁니다 — `ScoringContext`와 같은 방식입니다.
 *
 * 전부 코드가 이미 구해 둔 값입니다. 지표는 계산만 하고, 외부를 부르지 않습니다.
 *
 * @param monthlyTrades 매매. 오래된 달부터 정렬돼 있어야 합니다 — 추세 계산이 순서를 전제합니다
 * @param monthlyJeonse 순수 전세. 금액은 보증금입니다
 * @param loanRates     가계대출 금리 시계열 (ECOS,). 정렬은 보장하지 않는다
 * @param landUses      토지이용계획. 용도지역을 여기서 고른다
 * @param ledger        건축물대장 총괄표제부. 없으면 null — 용적률 여유를 내지 않는다
 * @param baseMonth     "최근"이 어느 달 기준인가.
 *                      목록의 마지막 항목이 아니라 오늘입니다. 못 받은 달은 목록에서
 *                      빠지므로, 위치로 창을 자르면 구멍이 있을 때 다른 달을 보게 됩니다.
 */
public record ForecastInput(
        Property property,
        List<MonthlyTrades> monthlyTrades,
        List<MonthlyTrades> monthlyJeonse,
        List<RatePoint> loanRates,
        List<LandUse> landUses,
        BuildingLedger ledger,
        YearMonth baseMonth
) {

    /** 기준 달을 안 주면 오늘이다. 기존 호출부를 그대로 두려는 것이기도 하다. */
    public ForecastInput(Property property, List<MonthlyTrades> monthlyTrades,
                         List<MonthlyTrades> monthlyJeonse, List<RatePoint> loanRates,
                         List<LandUse> landUses, BuildingLedger ledger) {
        this(property, monthlyTrades, monthlyJeonse, loanRates, landUses, ledger, YearMonth.now());
    }

    /** 실거래만 있으면 되는 지표를 위한 간편 생성 — 테스트와 초기 단계에서 쓴다. */
    public static ForecastInput ofTrades(Property property, List<MonthlyTrades> monthlyTrades) {
        return new ForecastInput(property, monthlyTrades, List.of(), List.of(), List.of(), null);
    }
}
