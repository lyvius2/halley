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

/** 전세가율. */
public class JeonseRatioIndicator implements PriceIndicator {

 /** 앞뒤로 비교할 구간(개월). 전세는 거래가 적어 매매보다 넓게 잡는다. */
    private static final int WINDOW_MONTHS = 6;
    private static final int REPORTING_LAG_MONTHS = 1;
    private static final int MIN_SAMPLES = 3;

    private final BigDecimal high;
    private final BigDecimal low;
    private final TradeStatCalculator calculator = new TradeStatCalculator();


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

 /** 수준이 먼저, 방향이 다음입니다. */
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
