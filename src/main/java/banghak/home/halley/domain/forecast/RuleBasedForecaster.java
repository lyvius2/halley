package banghak.home.halley.domain.forecast;

import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.forecast.indicator.PriceIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 규칙으로 방향을 낸다. */
public class RuleBasedForecaster {

 /** 무게별 표 수. 순서만 담고 정밀한 비율을 주장하지 않는다. */
    private static final int VOTES_HIGH = 3;
    private static final int VOTES_MEDIUM = 2;
    private static final int VOTES_LOW = 1;

 /** 이보다 표차가 적으면 방향을 주지 않습니다. HIGH 하나가 MEDIUM 하나를 이기는 정도(1표)는 */
    private static final int MIN_MARGIN = 2;

    private final List<PriceIndicator> indicators;
    private final int horizonMonths;

    public RuleBasedForecaster(List<PriceIndicator> indicators, int horizonMonths) {
        this.indicators = indicators == null ? List.of() : indicators;
        this.horizonMonths = horizonMonths;
    }


    public PriceOutlook forecast(ForecastInput input) {
        final List<PriceFactor> factors = new ArrayList<>();
        for (final PriceIndicator indicator : indicators) {
            final Optional<PriceFactor> factor = indicator.evaluate(input);
            factor.ifPresent(factors::add);
        }
        if (factors.isEmpty()) {
            return PriceOutlook.uncertain(horizonMonths, List.of("계산할 수 있는 지표가 없습니다"));
        }
        final int up = votes(factors, ForecastDirection.UP);
        final int down = votes(factors, ForecastDirection.DOWN);

        return new PriceOutlook(
                directionOf(up, down),
                confidenceOf(factors, up, down),
                horizonMonths,
                factors,
                caveats(factors));
    }

    private int votes(List<PriceFactor> factors, ForecastDirection direction) {
        return factors.stream()
                .filter(f -> f.effect() == direction)
                .mapToInt(f -> switch (f.weight()) {
                    case HIGH -> VOTES_HIGH;
                    case MEDIUM -> VOTES_MEDIUM;
                    case LOW -> VOTES_LOW;
                })
                .sum();
    }

 /** 표차가 작으면 FLAT입니다. 근소한 우위를 방향으로 읽지 않습니다. */
    private ForecastDirection directionOf(int up, int down) {
        if (up - down >= MIN_MARGIN) {
            return ForecastDirection.UP;
        }
        if (down - up >= MIN_MARGIN) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

 /** 요인들이 얼마나 한 방향인가. */
    private ForecastConfidence confidenceOf(List<PriceFactor> factors, int up, int down) {
        final int total = factors.stream()
                .mapToInt(f -> switch (f.weight()) {
                    case HIGH -> VOTES_HIGH;
                    case MEDIUM -> VOTES_MEDIUM;
                    case LOW -> VOTES_LOW;
                })
                .sum();
        if (total == 0) {
            return ForecastConfidence.LOW;
        }
        final int dominant = Math.max(up, down);
        final int opposed = Math.min(up, down);
        if (opposed > 0 || dominant * 2 <= total) {
            return ForecastConfidence.LOW;
        }
        return dominant * 4 >= total * 3 ? ForecastConfidence.HIGH : ForecastConfidence.MEDIUM;
    }

 /** 이 판단이 놓치고 있는 것. */
    private List<String> caveats(List<PriceFactor> factors) {
        final List<String> caveats = new ArrayList<>();
        caveats.add("정책 변화와 개별 단지의 수급은 반영하지 못했습니다");
        if (factors.size() < indicators.size()) {
            caveats.add(String.format("지표 %d개 중 %d개만 산출됐습니다",
                    indicators.size(), factors.size()));
        }
        if (factors.stream().noneMatch(f -> f.weight() == FactorWeight.HIGH)) {
            caveats.add("실거래 추세를 내지 못해 간접 지표만으로 본 것입니다");
        }
        return caveats;
    }
}
