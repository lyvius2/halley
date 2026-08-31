package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceForecast;

/**
 * 목록에 싣는 전망 요약 (설계 I136).
 *
 * <p><b>요인은 담지 않습니다.</b> 목록은 매물마다 하나씩 실려 나가는데 요인 전체를 담으면
 * 응답이 무거워집니다 — 상세는 모달을 열 때 받습니다(설계 5.3).
 *
 * @param running 지금 분석 중인가. 화살표 대신 <b>회색 표시</b>를 띄우는 데 쓴다
 */
public record ForecastSummary(
        String direction,
        String directionLabel,
        String confidenceLabel,
        int horizonMonths,
        boolean running
) {

    public static ForecastSummary from(PriceForecast forecast, boolean running) {
        return new ForecastSummary(
                forecast.outlook().direction().name(),
                forecast.outlook().direction().label(),
                forecast.outlook().confidence().label(),
                forecast.outlook().horizonMonths(),
                running);
    }

    /** 아직 결과가 없을 때. */
    public static ForecastSummary pending(boolean running) {
        return new ForecastSummary(ForecastDirection.UNCERTAIN.name(),
                ForecastDirection.UNCERTAIN.label(), null, 0, running);
    }
}
