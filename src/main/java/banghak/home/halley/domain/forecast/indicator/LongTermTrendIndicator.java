package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 4년에 걸친 연평균 변동률 (설계 I148).
 *
 * <p>실거래 추세(I130)가 <b>3개월 대 3개월</b>이라, 이 앱은 여태 <b>반년 안쪽만</b>
 * 보고 있었습니다. 60개월치를 받아 두고도 47개월은 아무도 안 읽었습니다.
 *
 * <p><b>기울기를 회귀로 구하지 않습니다.</b> 양 끝의 12개월 중앙값을 견주어
 * 연평균으로 환산합니다 — 표본이 얇은 달이 섞여도 흔들리지 않고,
 * <b>근거 문장에 그대로 적을 수 있습니다.</b> 회귀 계수는 사람이 검산할 수 없습니다.
 *
 * <p>단기와 어긋날 때가 이 지표의 값어치입니다.
 * <pre>
 * 장기 +2.1%/년, 최근 3개월 -5.8%  →  장기 상승 국면의 조정인가, 꺾임인가
 * </pre>
 * <b>그 판단은 LLM이 합니다.</b> 여기서는 두 숫자를 나란히 놓기만 합니다.
 */
public class LongTermTrendIndicator implements PriceIndicator {

    /** 양 끝에서 견줄 구간. 12개월이면 계절성이 상쇄된다. */
    private static final int WINDOW_MONTHS = 12;
    private static final int REPORTING_LAG_MONTHS = 1;
    private static final int MIN_SAMPLES = 3;
    /** 오래된 쪽 구간이 시작되는 지점. 48 + 12 = 60개월치를 쓴다. */
    private static final int BASE_OFFSET_MONTHS = 48;
    /** 두 구간 중심 사이의 햇수 — 연평균으로 환산할 때 쓴다. */
    private static final BigDecimal YEARS = BigDecimal.valueOf(4);

    private final BigDecimal threshold;

    /**
     * @param threshold 연 이만큼은 움직여야 방향으로 읽는다. <b>임의의 값입니다</b> —
     *                  물가상승률 언저리를 잡았을 뿐이라 `regulation_param`으로 조절합니다
     */
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
