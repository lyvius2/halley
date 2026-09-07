package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.PriceFactor;

import java.util.Optional;

/** 가격 요인 하나를 계산한다. */
public interface PriceIndicator {

    String code();


    Optional<PriceFactor> evaluate(ForecastInput input);
}
