package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/** 4년에 걸친 연평균 변동률. */
public class LongTermTrendIndicator implements PriceIndicator {

 /** 양 끝에서 견줄 구간. 12개월이면 계절성이 상쇄된다. */
    private static final int WINDOW_MONTHS = 12;
    private static final int REPORTING_LAG_MONTHS = 1;
    private static final int MIN_SAMPLES = 3;
 /** 오래된 쪽 구간이 시작되는 지점. 48 + 12 = 60개월치를 쓴다. */
    private static final int BASE_OFFSET_MONTHS = 48;
 /** 두 구간 중심 사이의 햇수. 연평균으로 환산할 때 쓴다. */
    private static final BigDecimal YEARS = BigDecimal.valueOf(4);

    private final BigDecimal threshold;


    public LongTermTrendIndicator(BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public String code() {
        return "LONG_TERM_TREND";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        final TradeStat recent = calc(input, 0);
        final TradeStat base = calc(input, BASE_OFFSET_MONTHS);
        if (recent.median() == null || base.median() == null
                || recent.count() < MIN_SAMPLES || base.count() < MIN_SAMPLES
                || base.median().signum() <= 0) {
            return Optional.empty();
        }
        final BigDecimal total = recent.median().subtract(base.median())
                .divide(base.median(), 6, RoundingMode.HALF_UP);
        final BigDecimal perYear = total.divide(YEARS, 6, RoundingMode.HALF_UP);

        return Optional.of(new PriceFactor(
                "장기 추세",
                directionOf(perYear),
                FactorWeight.MEDIUM,
                evidence(recent, base, total, perYear)));
    }

    private TradeStat calc(ForecastInput input, int offset) {
        return new TradeStatCalculator().medianOf(input.property(), input.monthlyTrades(),
                input.baseMonth(), offset, WINDOW_MONTHS, REPORTING_LAG_MONTHS);
    }

    private ForecastDirection directionOf(BigDecimal perYear) {
        if (perYear.compareTo(threshold) > 0) {
            return ForecastDirection.UP;
        }
        if (perYear.compareTo(threshold.negate()) < 0) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    private String evidence(TradeStat recent, TradeStat base, BigDecimal total, BigDecimal perYear) {
        return String.format("4년 전 12개월 중앙값 %s → 최근 12개월 %s (%s%%, 연 %s%%) · 표본 %d건 → %d건",
                money(base.median()), money(recent.median()),
                percent(total), percent(perYear), base.count(), recent.count());
    }

    private String percent(BigDecimal ratio) {
        final BigDecimal value = ratio.multiply(BigDecimal.valueOf(100), MathContext.DECIMAL64)
                .setScale(1, RoundingMode.HALF_UP);
        return value.signum() > 0 ? "+" + value.toPlainString() : value.toPlainString();
    }

    private String money(BigDecimal won) {
        return won.divide(BigDecimal.valueOf(100_000_000L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "억원";
    }
}
