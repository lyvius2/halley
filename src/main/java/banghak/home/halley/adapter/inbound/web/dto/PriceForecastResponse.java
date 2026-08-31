package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceForecast;

import java.time.Instant;
import java.util.List;

/**
 * 가격 전망 상세 (설계 I135).
 *
 * <p>목록에는 <b>요약만</b> 싣고 이 상세는 모달을 열 때 받습니다(설계 5.3) —
 * 요인 전체를 목록에 실으면 응답이 무거워집니다.
 *
 * @param codeDirection 규칙 기반 예측. <b>결론이 아니라 모달의 참고 문구용</b>입니다
 * @param agreed        둘이 같은 방향인가 — 문구를 가른다
 * @param running       지금 분석 중인가. 결과가 없을 때만 뜻이 있다
 */
public record PriceForecastResponse(
        Long propertyId,
        String direction,
        String directionLabel,
        String codeDirection,
        boolean agreed,
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
                forecast.codeDirection() == null ? null : forecast.codeDirection().name(),
                forecast.agreed(),
                forecast.outlook().confidence().name(),
                forecast.outlook().confidence().label(),
                forecast.outlook().horizonMonths(),
                forecast.outlook().factors().stream().map(Factor::from).toList(),
                forecast.outlook().caveats(),
                forecast.model(),
                forecast.computedAt(),
                running);
    }

    /** 아직 결과가 없을 때 — 분석 중인지만 알려 준다. */
    public static PriceForecastResponse pending(Long propertyId, boolean running) {
        return new PriceForecastResponse(propertyId, null, null, null, false,
                null, null, 0, List.of(), List.of(), null, null, running);
    }
}
