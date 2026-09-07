package banghak.home.halley.domain.forecast;

/** 가격에 작용하는 요인 하나. */
public record PriceFactor(
        String name,
        ForecastDirection effect,
        FactorWeight weight,
        String evidence
) {
}
