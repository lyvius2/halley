package banghak.home.halley.domain.forecast;

import java.util.List;

/** 가격 전망 하나. */
public record PriceOutlook(
        ForecastDirection direction,
        ForecastConfidence confidence,
        int horizonMonths,
        List<PriceFactor> factors,
        List<String> caveats
) {

 /** 재료가 모자라 판단하지 않은 경우. */
    public static PriceOutlook uncertain(int horizonMonths, List<String> caveats) {
        return new PriceOutlook(ForecastDirection.UNCERTAIN, ForecastConfidence.LOW,
                horizonMonths, List.of(), caveats);
    }
}
