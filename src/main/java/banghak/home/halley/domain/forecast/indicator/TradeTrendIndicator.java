package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;
import banghak.home.halley.domain.support.WonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
     * 이보다 표본이 적으면 <b>판단하지 않습니다.</b> 한 단지 한 면적대의 3개월 거래는
     * 흔히 3~10건입니다. 2건으로 낸 중앙값은 중앙값이라 부를 수 없습니다.
     */
    private static final int MIN_SAMPLES = 3;
    /** 국토부 신고 지연. 이번 달은 아직 덜 들어와 있어 뺀다. */
    private static final int REPORTING_LAG_MONTHS = 1;
    /**
     * 실거래 카드와 <b>같은 기준</b>을 씁니다(`ReferenceTransactionService.AREA_TOLERANCE`).
     * 두 화면이 다른 면적대를 보면 사용자가 헷갈립니다.
     */
    private static final BigDecimal AREA_TOLERANCE = new BigDecimal("0.15");
    /** 이보다 짧은 단지명은 우연히 걸린다 — 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

    private final BigDecimal threshold;

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
        final List<MonthlyTrades> monthly = input.monthly();
        if (monthly == null || monthly.isEmpty()) {
            return Optional.empty();
        }
        // 신고 지연분을 뺀 뒤 최근 3개월 / 그 앞 3개월
        final int end = monthly.size() - REPORTING_LAG_MONTHS;
        if (end < WINDOW_MONTHS * 2) {
            return Optional.empty();
        }
        final TradeStat recent = stat(input.property(), monthly.subList(end - WINDOW_MONTHS, end));
        final TradeStat previous = stat(input.property(),
                monthly.subList(Math.max(0, end - WINDOW_MONTHS * 2), end - WINDOW_MONTHS));

        if (recent.count() < MIN_SAMPLES || previous.count() < MIN_SAMPLES) {
            // 표본이 얇으면 <b>내지 않습니다</b>. 억지로 방향을 주면 없는 신호를 만듭니다
            return Optional.empty();
        }
        final BigDecimal change = recent.median()
                .subtract(previous.median())
                .divide(previous.median(), 6, RoundingMode.HALF_UP);

        return Optional.of(new PriceFactor(
                "실거래 추세",
                directionOf(change),
                FactorWeight.HIGH,
                evidence(recent, previous, change)));
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
    private String evidence(TradeStat recent, TradeStat previous, BigDecimal change) {
        return String.format("직전 %d개월 중앙값 %s → 최근 %d개월 %s (%s%.1f%%, 표본 %d건 → %d건)",
                WINDOW_MONTHS, WonFormat.of(previous.median().longValue()),
                WINDOW_MONTHS, WonFormat.of(recent.median().longValue()),
                change.signum() >= 0 ? "+" : "",
                change.multiply(BigDecimal.valueOf(100)).doubleValue(),
                previous.count(), recent.count());
    }

    /**
     * 같은 단지·면적대의 거래만 골라 중앙값을 낸다.
     *
     * <p><b>평균이 아니라 중앙값입니다.</b> 표본이 얇아 대형 평형 한 건이 섞이면
     * 평균은 통째로 끌려갑니다.
     */
    private TradeStat stat(Property property, List<MonthlyTrades> window) {
        final List<Long> prices = new ArrayList<>();
        for (final MonthlyTrades month : window) {
            for (final ReferenceTrade trade : month.trades()) {
                if (matches(property, trade) && trade.dealAmount() != null) {
                    prices.add(trade.dealAmount());
                }
            }
        }
        if (prices.isEmpty()) {
            return new TradeStat(null, 0);
        }
        prices.sort(Comparator.naturalOrder());
        return new TradeStat(median(prices), prices.size());
    }

    private BigDecimal median(List<Long> sorted) {
        final int n = sorted.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(n / 2));
        }
        return BigDecimal.valueOf(sorted.get(n / 2 - 1) + sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

    /**
     * 같은 단지·같은 면적대인가.
     *
     * <p>단지명은 표기가 흔들리므로(`래미안` vs `래미안아파트`) <b>서로 포함</b>이면 같게 봅니다.
     * 면적은 매물의 전용면적 ±15%.
     */
    private boolean matches(Property property, ReferenceTrade trade) {
        return sameName(property, trade) && sameArea(property, trade);
    }

    private boolean sameName(Property property, ReferenceTrade trade) {
        final String mine = normalize(property.name());
        final String theirs = normalize(trade.apartmentName());
        if (mine == null || theirs == null || mine.length() < MIN_NAME_LENGTH) {
            // 이름을 못 가리면 면적으로만 본다 — 같은 법정동이라 아주 틀리진 않는다
            return true;
        }
        return mine.contains(theirs) || theirs.contains(mine);
    }

    private boolean sameArea(Property property, ReferenceTrade trade) {
        final BigDecimal mine = property.areaExclusiveM2();
        final BigDecimal theirs = trade.areaM2();
        if (mine == null || theirs == null || mine.signum() <= 0) {
            return true;
        }
        final BigDecimal gap = theirs.subtract(mine).abs()
                .divide(mine, 6, RoundingMode.HALF_UP);
        return gap.compareTo(AREA_TOLERANCE) <= 0;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("[\\s()·\\-]", "").replace("아파트", "");
    }
}
