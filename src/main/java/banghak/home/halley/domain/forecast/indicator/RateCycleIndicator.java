package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.loan.RatePoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 금리 국면 (설계 I131).
 *
 * <p>가계대출 금리가 내려가는 국면이면 <b>매수 여력이 커집니다.</b> 올라가면 반대입니다.
 * 재료는 ECOS에서 이미 받고 있습니다(I116) — 스트레스 금리를 만들려고 붙인 것을 함께 씁니다.
 *
 * <p><b>무게는 MEDIUM입니다.</b> 시장 전체의 이야기라 이 단지에 그대로 오지 않습니다.
 * 실거래 추세(HIGH)와 층위가 다릅니다.
 */
public class RateCycleIndicator implements PriceIndicator {

    /** 몇 달을 놓고 기울기를 보는가. 12개월이면 계절성 한 바퀴가 들어간다. */
    private static final int WINDOW_MONTHS = 12;
    /**
     * 이보다 적게 움직였으면 국면이라 부르지 않습니다. 0.25%p는 기준금리 한 번의 폭입니다 —
     * 그보다 작은 변화를 방향으로 읽으면 잡음을 신호로 만듭니다.
     */
    private static final BigDecimal THRESHOLD = new BigDecimal("0.0025");
    private static final int MIN_SAMPLES = 6;

    @Override
    public String code() {
        return "RATE_CYCLE";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        final List<RatePoint> rates = input.loanRates();
        if (rates == null || rates.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        final List<RatePoint> sorted = rates.stream()
                .sorted(Comparator.comparing(RatePoint::month))
                .toList();
        final List<RatePoint> window = sorted.size() <= WINDOW_MONTHS
                ? sorted
                : sorted.subList(sorted.size() - WINDOW_MONTHS, sorted.size());

        final RatePoint from = window.getFirst();
        final RatePoint to = window.getLast();
        final BigDecimal change = to.rate().subtract(from.rate());

        return Optional.of(new PriceFactor(
                "금리 국면",
                // 금리가 내리면 매수 여력이 커진다 — 부호가 반대다
                directionOf(change),
                FactorWeight.MEDIUM,
                String.format("가계대출금리 %s%% (%s) → %s%% (%s), %s%.2f%%p",
                        percent(from.rate()), from.month(),
                        percent(to.rate()), to.month(),
                        change.signum() >= 0 ? "+" : "",
                        change.multiply(BigDecimal.valueOf(100)).doubleValue())));
    }

    /** <b>금리가 내리면 UP입니다.</b> 부호가 뒤집히는 자리라 따로 둡니다. */
    private ForecastDirection directionOf(BigDecimal change) {
        if (change.compareTo(THRESHOLD.negate()) < 0) {
            return ForecastDirection.UP;
        }
        if (change.compareTo(THRESHOLD) > 0) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    private String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
