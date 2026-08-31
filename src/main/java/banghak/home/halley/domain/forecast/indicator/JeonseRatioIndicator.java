package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 전세가율 (설계 I131).
 *
 * <pre>
 *   전세가율 = median(전세 보증금) / median(매매가)     ← 같은 면적대
 * </pre>
 *
 * <p><b>수준과 방향을 같이 봅니다.</b>
 * 높으면(70%+) 실거주 수요가 받쳐 하방이 단단하고, 낮으면(50% 미만) 매매가에
 * 기대가 많이 실려 있다는 뜻입니다. 다만 <b>둘 다 방향을 단정하지는 못합니다</b> —
 * 요인 하나일 뿐입니다.
 *
 * <p><b>무게는 MEDIUM입니다.</b> 고전적인 선행 지표지만 이 단지의 실거래만큼 직접적이지 않습니다.
 */
public class JeonseRatioIndicator implements PriceIndicator {

    /** 앞뒤로 비교할 구간(개월). 전세는 거래가 적어 매매보다 넓게 잡는다. */
    private static final int WINDOW_MONTHS = 6;
    private static final int REPORTING_LAG_MONTHS = 1;
    private static final int MIN_SAMPLES = 3;

    private final BigDecimal high;
    private final BigDecimal low;
    private final TradeStatCalculator calculator = new TradeStatCalculator();

    /**
     * @param high 이 위면 실거주 수요가 받친다고 본다 (기본 0.70)
     * @param low  이 아래면 매매가에 기대가 실려 있다고 본다 (기본 0.50)
     */
    public JeonseRatioIndicator(BigDecimal high, BigDecimal low) {
        this.high = high;
        this.low = low;
    }

    @Override
    public String code() {
        return "JEONSE_RATIO";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        final BigDecimal recent = ratio(input.property(), input.monthlyTrades(), input.monthlyJeonse(),
                input.baseMonth(), 0);
        if (recent == null) {
            return Optional.empty();
        }
        final BigDecimal before = ratio(input.property(), input.monthlyTrades(), input.monthlyJeonse(),
                input.baseMonth(), WINDOW_MONTHS);

        return Optional.of(new PriceFactor(
                "전세가율",
                directionOf(recent, before),
                FactorWeight.MEDIUM,
                evidence(recent, before)));
    }

    /**
     * 수준이 먼저, 방향이 다음입니다.
     *
     * <p><b>둘 다 애매하면 FLAT입니다.</b> 억지로 방향을 주지 않습니다.
     */
    private ForecastDirection directionOf(BigDecimal recent, BigDecimal before) {
        final boolean rising = before != null && recent.compareTo(before) > 0;
        final boolean falling = before != null && recent.compareTo(before) < 0;

        if (recent.compareTo(high) >= 0 && !falling) {
            return ForecastDirection.UP;
        }
        if (recent.compareTo(low) < 0 && !rising) {
            return ForecastDirection.DOWN;
        }
        return ForecastDirection.FLAT;
    }

    private String evidence(BigDecimal recent, BigDecimal before) {
        if (before == null) {
            return String.format("전세가율 %s%% (직전 구간은 표본이 모자라 비교하지 않음)", percent(recent));
        }
        return String.format("전세가율 %s%% → %s%% (%d개월 전 대비)",
                percent(before), percent(recent), WINDOW_MONTHS);
    }

    /**
     * @param offset 몇 달 전 구간을 볼지. 0이면 최근
     * @return 표본이 모자라면 null — <b>0으로 두면 그 값이 계산에 섞입니다</b>
     */
    private BigDecimal ratio(Property property, List<MonthlyTrades> trades,
                             List<MonthlyTrades> jeonse, java.time.YearMonth base, int offset) {
        final TradeStat sale = calculator.medianOf(property, trades, base, offset, WINDOW_MONTHS,
                REPORTING_LAG_MONTHS);
        final TradeStat rent = calculator.medianOf(property, jeonse, base, offset, WINDOW_MONTHS,
                REPORTING_LAG_MONTHS);
        if (sale.count() < MIN_SAMPLES || rent.count() < MIN_SAMPLES
                || sale.median() == null || sale.median().signum() <= 0) {
            return null;
        }
        return rent.median().divide(sale.median(), 4, RoundingMode.HALF_UP);
    }

    private String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
