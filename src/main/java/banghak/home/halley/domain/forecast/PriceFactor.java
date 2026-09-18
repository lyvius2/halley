package banghak.home.halley.domain.forecast;

public record PriceFactor(
        String name,
        ForecastDirection effect,
        FactorWeight weight,
        String evidence
) {
}
