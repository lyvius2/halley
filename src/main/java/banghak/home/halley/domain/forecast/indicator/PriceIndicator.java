package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.PriceFactor;

import java.util.Optional;

public interface PriceIndicator {

    String code();

    Optional<PriceFactor> evaluate(ForecastInput input);
}
