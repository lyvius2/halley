package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.support.WonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class TradeTrendIndicator implements PriceIndicator {

    /** 앞뒤로 비교할 구간의 길이(개월). 3개월이면 계절성에 덜 흔들린다.  */
    private static final int WINDOW_MONTHS = 3;
    private static final int WIDE_WINDOW_MONTHS = 6;
    private static final int MIN_SAMPLES = 3;
    /** 국토부 신고 지연. 이번 달은 아직 덜 들어와 있어 뺀다.  */
    private static final int REPORTING_LAG_MONTHS = 1;
    private final BigDecimal threshold;
    /** 면적·이름 기준을 전세가율과 같게 두려고 공유합니다.  */
    private final TradeStatCalculator calculator = new TradeStatCalculator();

    public TradeTrendIndicator(BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public String code() {
        return "TRADE_TREND";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        // 3개월로 되면 그대로, 안 되면 6개월로 넓혀 한 번 더 본다
        final Window window = widestThatFits(input);
        if (window == null) {
            // 넓혀도 표본이 얇으면 내지 않습니다. 억지로 방향을 주면 없는 신호를 만듭니다
            return Optional.empty();
        }
        final BigDecimal change = window.recent().median()
                .subtract(window.previous().median())
                .divide(window.previous().median(), 6, RoundingMode.HALF_UP);

        return Optional.of(new PriceFactor(
                "실거래 추세",
                directionOf(change),
                FactorWeight.HIGH,
                evidence(window, change)));
    }

    private Window widestThatFits(ForecastInput input) {
        for (final int months : new int[]{WINDOW_MONTHS, WIDE_WINDOW_MONTHS}) {
            final TradeStat recent = calculator.medianOf(input.property(), input.monthlyTrades(),
                    input.baseMonth(), 0, months, REPORTING_LAG_MONTHS);
            final TradeStat previous = calculator.medianOf(input.property(), input.monthlyTrades(),
                    input.baseMonth(), months, months, REPORTING_LAG_MONTHS);
            if (recent.count() >= MIN_SAMPLES && previous.count() >= MIN_SAMPLES) {
                return new Window(months, recent, previous);
            }
        }
        return null;
    }

    /** 어느 길이의 창으로 쟀는가. 넓혔다면 근거 문장이 그렇게 말한다  */
    private record Window(int months, TradeStat recent, TradeStat previous) {
    }

    private ForecastDirection directionOf(BigDecimal change) {
        if (change.compareTo(threshold) > 0) {
            return ForecastDirection.UP;
        }
        if (change.compareTo(threshold.negate()) < 0) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    private String evidence(Window window, BigDecimal change) {
        final String widened = window.months() > WINDOW_MONTHS
                ? " · 거래가 드물어 창을 넓혀 쟀습니다"
                : "";
        return String.format("직전 %d개월 중앙값 %s → 최근 %d개월 %s (%s%.1f%%, 표본 %d건 → %d건)%s",
                window.months(), WonFormat.of(window.previous().median().longValue()),
                window.months(), WonFormat.of(window.recent().median().longValue()),
                change.signum() >= 0 ? "+" : "",
                change.multiply(BigDecimal.valueOf(100)).doubleValue(),
                window.previous().count(), window.recent().count(), widened);
    }






}
