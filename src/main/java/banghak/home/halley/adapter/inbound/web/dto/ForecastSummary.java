package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.FactorTally;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceForecast;

/**
 * 목록에 싣는 전망 요약.
 *
 * 요인은 담지 않습니다. 목록은 매물마다 하나씩 실려 나가는데 요인 전체를 담으면
 * 응답이 무거워집니다 — 상세는 모달을 열 때 받습니다.
 *
 * @param running 지금 분석 중인가. 화살표 대신 회색 표시를 띄우는 데 쓴다
 * @param stored   전망을 낸 적이 있는가. direction으로는 알 수 없다 —
 *                 결과가 없을 때도, 판단을 못 했을 때도 똑같이 UNCERTAIN이다.
 *                 "모르겠다고 답한 것"과 "아직 안 물어본 것"은 다르다.
 * @param noSignal 셀 것이 없었는가. 지표가 하나도 없거나 전부 유지인 경우다.
 *                 방향으로는 둘 다 유지지만 카드는 🤔 를 붙여 다르게 보여 준다 —
 *                 "재료를 보고 판단을 안 한 것"과 "볼 재료가 없었던 것"은 다른 이야기다
 */
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
