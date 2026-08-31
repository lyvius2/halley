package banghak.home.halley.domain.forecast;

import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.forecast.indicator.PriceIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 규칙으로 방향을 낸다 (설계 I133).
 *
 * <p><b>이것은 결론이 아닙니다.</b> 결론은 LLM이 냅니다(4.5). 이 예측은
 * <b>눈가림 2차 소견</b>으로, 화면에서 "AI와 규칙 계산이 갈렸습니다"를 말하는 데 씁니다.
 * <b>LLM에게는 넘기지 않습니다</b> — 보여 주면 모델이 끌려가 두 예측이 독립이 아니게 됩니다.
 *
 * <h4>합산하지 않습니다</h4>
 *
 * <p>요인을 점수로 합치려면 가중치가 필요한데 <b>그 근거가 없습니다.</b> 합치는 순간
 * 임의의 숫자가 객관적 예측처럼 보입니다. 대신 <b>세어서</b> 정합니다.
 *
 * <pre>
 *   방향  = 무게를 고려해 표를 세고, 이긴 쪽
 *   확신도 = 요인들이 얼마나 한 방향인가
 * </pre>
 *
 * <p>무게(HIGH·MEDIUM·LOW)는 표의 개수로만 씁니다 — <b>실거래 추세 한 표가
 * 용도지역 한 표보다 무겁다</b>는 정도의 순서만 담습니다. 소수점 가중치를 두지 않는 이유는
 * 그 숫자를 정당화할 방법이 없기 때문입니다.
 */
public class RuleBasedForecaster {

    /** 무게별 표 수. 순서만 담고 정밀한 비율을 주장하지 않는다. */
    private static final int VOTES_HIGH = 3;
    private static final int VOTES_MEDIUM = 2;
    private static final int VOTES_LOW = 1;

    /**
     * 이보다 표차가 적으면 방향을 주지 않습니다. HIGH 하나가 MEDIUM 하나를 이기는 정도(1표)는
     * <b>이겼다고 보기 어렵습니다.</b>
     */
    private static final int MIN_MARGIN = 2;

    private final List<PriceIndicator> indicators;
    private final int horizonMonths;

    public RuleBasedForecaster(List<PriceIndicator> indicators, int horizonMonths) {
        this.indicators = indicators == null ? List.of() : indicators;
        this.horizonMonths = horizonMonths;
    }

    /**
     * @return 요인이 하나도 없으면 {@code UNCERTAIN}. <b>억지로 방향을 고르지 않습니다.</b>
     */
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

    /** 표차가 작으면 <b>FLAT입니다.</b> 근소한 우위를 방향으로 읽지 않습니다. */
    private ForecastDirection directionOf(int up, int down) {
        if (up - down >= MIN_MARGIN) {
            return ForecastDirection.UP;
        }
        if (down - up >= MIN_MARGIN) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    /**
     * 요인들이 얼마나 한 방향인가.
     *
     * <p><b>방향이 갈리는데 확신이 높을 수는 없습니다.</b> 표가 한쪽으로 몰릴수록 높습니다.
     */
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
        // 반대 방향 표가 섞이면 확신이 떨어진다
        final int dominant = Math.max(up, down);
        final int opposed = Math.min(up, down);
        if (opposed > 0 || dominant * 2 <= total) {
            return ForecastConfidence.LOW;
        }
        return dominant * 4 >= total * 3 ? ForecastConfidence.HIGH : ForecastConfidence.MEDIUM;
    }

    /**
     * 이 판단이 놓치고 있는 것.
     *
     * <p><b>비워 두지 않습니다.</b> 재료가 없는 것은 늘 있고, 그것을 말하지 않으면
     * 사용자는 이 판단이 모든 것을 봤다고 여깁니다.
     */
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
