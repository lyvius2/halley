package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.FactorTally;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceForecast;

/** 목록에 싣는 전망 요약. */
public record ForecastSummary(
        String direction,
        String directionLabel,
        String confidenceLabel,
        int horizonMonths,
        boolean running,
        boolean stored,
        boolean noSignal
) {

    public static ForecastSummary from(PriceForecast forecast, boolean running) {
        return new ForecastSummary(
                forecast.outlook().direction().name(),
                forecast.outlook().direction().label(),
                forecast.outlook().confidence().label(),
                forecast.outlook().horizonMonths(),
                running,
                true,
                FactorTally.of(forecast.outlook().factors()).noSignal());
    }

 /** 아직 결과가 없을 때. */
    public static ForecastSummary pending(boolean running) {
        return new ForecastSummary(ForecastDirection.UNCERTAIN.name(),
                ForecastDirection.UNCERTAIN.label(), null, 0, running, false, true);
    }
}
