package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.support.WonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 실거래 추세 (설계 I130).
 *
 * <pre>
 *   최근 = median(최근 3개월, 같은 단지·면적대)
 *   직전 = median(그 앞 3개월)
 *   변동률 = (최근 − 직전) / 직전
 * </pre>
 *
 * <p><b>이 지표는 방향을 단정하지 않습니다.</b> 요인 하나의 방향을 낼 뿐이고,
 * 종합 판단은 LLM이 합니다(4.5). 임계값은 <b>요인의 방향</b>을 가르는 데만 씁니다.
 *
 * <p><b>가장 직접적인 신호라 무게는 항상 HIGH입니다.</b> "이 집이 실제로 얼마에
 * 팔렸나"이고, 나머지 지표는 시장 전체의 이야기라 이 단지에 그대로 오지 않습니다.
 */
public class TradeTrendIndicator implements PriceIndicator {

    /** 앞뒤로 비교할 구간의 길이(개월). 3개월이면 계절성에 덜 흔들린다. */
    private static final int WINDOW_MONTHS = 3;
    /**
     * 3개월로 표본이 안 차면 여기까지 넓혀 본다 (설계 I252).
     *
     * <p><b>작은 단지가 늘 판단 보류였습니다.</b> 345세대 단지의 한 평형은 1년에
     * 8건쯤 팔립니다 — 3개월 창에 3건은 채우기 어렵습니다. 그렇다고 표본 기준을
     * 낮추면 <b>2건짜리 중앙값</b>을 추세라 부르게 되어 [I130]의 취지가 무너집니다.
     *
     * <p>기준을 낮추는 대신 <b>기간을 늘립니다.</b> "표본이 얇으니 더 긴 기간을
     * 본다"는 정직한 대응이고, 넓혔다는 사실은 근거 문장에 적힙니다.
     */
    private static final int WIDE_WINDOW_MONTHS = 6;
    /**
     * 이보다 표본이 적으면 <b>판단하지 않습니다.</b> 한 단지 한 면적대의 3개월 거래는
     * 흔히 3~10건입니다. 2건으로 낸 중앙값은 중앙값이라 부를 수 없습니다.
     */
    private static final int MIN_SAMPLES = 3;
    /** 국토부 신고 지연. 이번 달은 아직 덜 들어와 있어 뺀다. */
    private static final int REPORTING_LAG_MONTHS = 1;
    private final BigDecimal threshold;
    /** 면적·이름 기준을 전세가율과 <b>같게</b> 두려고 공유합니다 (설계 I131). */
    private final TradeStatCalculator calculator = new TradeStatCalculator();

    /**
     * @param threshold 이만큼 넘게 움직여야 방향을 준다. <b>임의의 값입니다</b> —
     *                  부동산 월간 변동의 잡음이 대략 이 정도라 잡았을 뿐이라
     *                  `regulation_param`으로 조절합니다 (설계 4.5)
     */
    public TradeTrendIndicator(BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public String code() {
        return "TRADE_TREND";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        // 3개월로 되면 그대로, 안 되면 6개월로 넓혀 한 번 더 본다 (설계 I252)
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

    /**
     * 표본이 차는 <b>가장 좁은</b> 창을 고른다 (설계 I252).
     *
     * <p>좁을수록 최근을 잘 비춥니다. 3개월로 되면 굳이 넓히지 않습니다.
     */
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

    /** 어느 길이의 창으로 쟀는가. <b>넓혔다면 근거 문장이 그렇게 말한다</b> (설계 I252) */
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

    /**
     * 근거 문장. <b>표본 수를 반드시 넣습니다</b> — 3건으로 낸 판단과 30건으로 낸 판단은
     * 다르고, 사용자가 그것을 알아야 합니다.
     */
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
