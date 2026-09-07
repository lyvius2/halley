package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.building.BuildingLedger;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.loan.RatePoint;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.time.YearMonth;
import java.util.List;

/** 지표 계산에 넣는 재료. */
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

 /** 실거래만 있으면 되는 지표를 위한 간편 생성. 테스트와 초기 단계에서 쓴다. */
    public static ForecastInput ofTrades(Property property, List<MonthlyTrades> monthlyTrades) {
        return new ForecastInput(property, monthlyTrades, List.of(), List.of(), List.of(), null);
    }
}
