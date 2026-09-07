package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** 5년이 어떤 모양으로 움직였는가. */
public final class YearlyMedians {

 /** 몇 해를 보여 줄까. 5년치를 받아 두므로 그만큼이다 */
    private static final int YEARS = 5;
 /** 이보다 적은 해는 중앙값이라 부를 수 없다. 건수를 함께 적어 그대로 보여 준다 */
    private static final int MIN_SAMPLES = 2;
 /** 해가 이만큼은 있어야 '모양'이라 할 수 있다 */
    private static final int MIN_YEARS = 3;

    private final TradeStatCalculator calculator = new TradeStatCalculator();

 /** 해가 모자라면 null. 없는 모양을 지어내지 않는다 */
    public String describe(Property property, List<MonthlyTrades> monthly, int newestYear) {
        final Map<Integer, List<Long>> byYear = new LinkedHashMap<>();
        for (final MonthlyTrades month : monthly == null ? List.<MonthlyTrades>of() : monthly) {
            if (month == null || month.dealYm() == null || month.trades() == null
                    || month.dealYm().getYear() < newestYear - YEARS) {
                continue;
            }
            for (final ReferenceTrade trade : month.trades()) {
                if (trade.dealAmount() != null && calculator.matchesProperty(property, trade)) {
                    byYear.computeIfAbsent(month.dealYm().getYear(), y -> new ArrayList<>())
                            .add(trade.dealAmount());
                }
            }
        }
        final List<Integer> years = byYear.keySet().stream()
                .filter(y -> byYear.get(y).size() >= MIN_SAMPLES)
                .sorted()
                .toList();
        if (years.size() < MIN_YEARS) {
            return null;
        }
        final StringJoiner sb = new StringJoiner(" · ");
        for (final Integer year : years) {
            final List<Long> amounts = byYear.get(year);
            sb.add(String.format("%d년 %s (%d건)", year, money(median(amounts)), amounts.size()));
        }
        return sb.toString();
    }

    private BigDecimal median(List<Long> amounts) {
        final List<Long> sorted = amounts.stream().sorted(Comparator.naturalOrder()).toList();
        final int n = sorted.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(n / 2));
        }
        return BigDecimal.valueOf(sorted.get(n / 2 - 1) + sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal won) {
        return won.divide(BigDecimal.valueOf(100_000_000L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "억";
    }
}
