package banghak.home.halley.domain.forecast;

import java.time.Instant;

public record PriceForecast(
        Long id,
        Long propertyId,
        PriceOutlook outlook,
        ForecastDirection llmDirection,
        ForecastDirection codeDirection,
        String promptHash,
        String model,
        Instant computedAt
) {

    /** 두 예측이 같은 방향인가 — 모달의 참고 문구를 가른다.  */
    public boolean agreed() {
        return outlook.direction() == codeDirection;
    }

    public boolean strong() {
        if (llmDirection != ForecastDirection.UP && llmDirection != ForecastDirection.DOWN) {
            return false;
        }
        return FactorTally.of(outlook.factors()).direction() == llmDirection;
    }
}
