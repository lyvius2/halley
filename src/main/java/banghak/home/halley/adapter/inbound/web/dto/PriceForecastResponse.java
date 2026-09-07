package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceForecast;

import java.time.Instant;
import java.util.List;

/** 가격 전망 상세. */
public record PriceForecastResponse(
        Long propertyId,
        String direction,
        String directionLabel,
        String llmDirection,
        String codeDirection,
        boolean agreed,
        boolean strong,
        String confidence,
        String confidenceLabel,
        int horizonMonths,
        List<Factor> factors,
        List<String> caveats,
        String model,
        Instant computedAt,
        boolean running
) {

    public record Factor(String name, String effect, String effectLabel,
                         String weight, String weightLabel, String evidence) {

        static Factor from(PriceFactor factor) {
            return new Factor(factor.name(),
                    factor.effect().name(), factor.effect().label(),
                    factor.weight().name(), factor.weight().label(),
                    factor.evidence());
        }
    }

    public static PriceForecastResponse from(PriceForecast forecast, boolean running) {
        return new PriceForecastResponse(
                forecast.propertyId(),
                forecast.outlook().direction().name(),
                forecast.outlook().direction().label(),
                forecast.llmDirection() == null ? null : forecast.llmDirection().name(),
                forecast.codeDirection() == null ? null : forecast.codeDirection().name(),
                forecast.agreed(),
                forecast.strong(),
                forecast.outlook().confidence().name(),
                forecast.outlook().confidence().label(),
                forecast.outlook().horizonMonths(),
                forecast.outlook().factors().stream().map(Factor::from).toList(),
                forecast.outlook().caveats(),
                forecast.model(),
                forecast.computedAt(),
                running);
    }

 /** 아직 결과가 없을 때. 분석 중인지만 알려 준다. */
    public static PriceForecastResponse pending(Long propertyId, boolean running) {
        return new PriceForecastResponse(propertyId, null, null, null, null, false, false,
                null, null, 0, List.of(), List.of(), null, null, running);
    }
}
