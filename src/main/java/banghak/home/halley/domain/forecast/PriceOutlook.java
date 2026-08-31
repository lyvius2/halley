package banghak.home.halley.domain.forecast;

import java.util.List;

/**
 * 가격 전망 하나 (설계 I133).
 *
 * @param direction  결론. LLM이 있으면 <b>LLM의 판단</b>이고, 없으면 코드 예측이다 (4.5)
 * @param confidence 확신도
 * @param factors    요인들. <b>근거 없는 요인은 담지 않습니다</b>
 * @param caveats    이 판단이 놓치고 있는 것. <b>비워 두지 않습니다</b>
 */
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
