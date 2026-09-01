package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 5년 전고점 대비 지금 어디쯤인가 (설계 I148).
 *
 * <p>실거래 추세(I130)와 <b>보는 것이 다릅니다.</b> 그쪽은 최근 3개월이 직전 3개월보다
 * 올랐나를 보는 <b>모멘텀</b>이고, 여기는 5년 안에서 지금이 높은 자리인가를 보는
 * <b>위치</b>입니다.
 *
 * <p><b>둘은 자주 어긋납니다.</b> 고점 근처에서 오르는 중일 수 있습니다 —
 * 그때 추세는 UP, 위치는 DOWN을 냅니다. <b>그 어긋남을 지우지 않습니다.</b>
 * 저울질은 LLM이 합니다(2.2). 요인을 하나로 합치면 그 재료가 사라집니다.
 *
 * <p><b>평균회귀를 가정합니다.</b> 고점에 붙어 있으면 더 오를 여력이 적다고 봅니다 —
 * 맞는다는 보장은 없고, 모멘텀과 반대 방향의 시각을 하나 넣어 두는 것입니다.
 * 가중치를 MEDIUM으로 두는 이유이기도 합니다.
 */
public class PeakDistanceIndicator implements PriceIndicator {

    /** 최근 자리를 재는 창. 실거래 추세와 같게 둔다 — 다른 창을 쓰면 두 요인이 다른 것을 잰다. */
    private static final int WINDOW_MONTHS = 3;
    private static final int REPORTING_LAG_MONTHS = 1;
    private static final int MIN_SAMPLES = 3;
    /** 고점을 찾을 범위. 5년이면 한 번의 상승·조정 국면이 들어간다. */
    private static final int SPAN_MONTHS = 60;
    /**
     * 고점이라 부르려면 창이 이만큼은 있어야 한다. 서너 창으로 고른 '최고값'은
     * <b>그냥 그 몇 개 중 최고</b>일 뿐이다.
     */
    private static final int MIN_WINDOWS = 12;

    private final BigDecimal nearRatio;
    private final BigDecimal farRatio;
    private final TradeStatCalculator calculator = new TradeStatCalculator();

    /**
     * @param nearRatio 이 위면 고점에 붙었다고 본다
     * @param farRatio  이 아래면 고점과 멀다고 본다
     */
    public PeakDistanceIndicator(BigDecimal nearRatio, BigDecimal farRatio) {
        this.nearRatio = nearRatio;
        this.farRatio = farRatio;
    }

    @Override
    public String code() {
        return "PEAK_DISTANCE";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        final TradeStat recent = calculator.medianOf(input.property(), input.monthlyTrades(),
                input.baseMonth(), 0, WINDOW_MONTHS, REPORTING_LAG_MONTHS);
        if (recent.median() == null || recent.count() < MIN_SAMPLES) {
            return Optional.empty();
        }
        final List<TradeStat> windows = calculator.rollingMedians(input.property(), input.monthlyTrades(),
                input.baseMonth(), SPAN_MONTHS, WINDOW_MONTHS, REPORTING_LAG_MONTHS, MIN_SAMPLES);
        if (windows.size() < MIN_WINDOWS) {
            // 자료가 얇으면 '고점'이 고점이 아니다
            return Optional.empty();
        }
        final BigDecimal peak = windows.stream()
                .map(TradeStat::median)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        if (peak.signum() <= 0) {
            return Optional.empty();
        }
        final BigDecimal ratio = recent.median().divide(peak, 4, RoundingMode.HALF_UP);

        return Optional.of(new PriceFactor(
                "전고점 대비",
                directionOf(ratio),
                FactorWeight.MEDIUM,
                evidence(recent, peak, ratio, windows.size())));
    }

    private ForecastDirection directionOf(BigDecimal ratio) {
        if (ratio.compareTo(farRatio) <= 0) {
            return ForecastDirection.UP;
        }
        if (ratio.compareTo(nearRatio) >= 0) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    /** <b>고점이 언제였는지는 적지 않습니다</b> — 창을 겹쳐 훑어 특정 달로 못 박을 수 없습니다. */
    private String evidence(TradeStat recent, BigDecimal peak, BigDecimal ratio, int windows) {
        return String.format("최근 3개월 중앙값 %s, 5년 전고점 %s → 고점 대비 %s%% (비교 구간 %d개)",
                money(recent.median()), money(peak),
                ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString(),
                windows);
    }

    private String money(BigDecimal won) {
        return won.divide(BigDecimal.valueOf(100_000_000L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "억원";
    }
}
