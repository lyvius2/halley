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

/**
 * 5년이 <b>어떤 모양으로</b> 움직였는가 (설계 I255).
 *
 * <h4>왜 필요한가</h4>
 *
 * <p>지금 LLM 에 가는 것은 <b>점 몇 개</b>입니다 — 4년 전 12개월 중앙값, 최근 12개월
 * 중앙값, 5년 전고점, 최근 3개월. <b>그 사이가 어떻게 움직였는지는 안 갑니다.</b>
 *
 * <pre>
 * 같은 +7.6% 라도 전혀 다른 이야기다
 *   꾸준히 올랐다     5.95 → 6.1 → 6.3 → 6.4 → 6.4
 *   올랐다가 꺾였다   5.95 → 7.4 → 7.2 → 6.6 → 6.4   ← 지금 내려오는 중
 * </pre>
 *
 * <p>전고점 대비가 두 번째를 어느 정도 잡아 주지만 <b>언제 꺾였는지</b>는 모릅니다.
 *
 * <h4>지표가 아니다</h4>
 *
 * <p><b>{@link PriceIndicator} 로 만들지 않았습니다.</b> 지표로 내면 {@code FLAT}
 * 한 표가 되어 <b>판단 보류를 늘립니다</b> — [I248]에서 "유지가 과반이면 무조건 유지"로
 * 정했기 때문입니다. 같은 움직임을 실거래 추세·장기 추세가 이미 세고 있어,
 * 여기서 또 한 표를 주면 <b>한 사실을 두 번 세게</b> 됩니다.
 *
 * <p>프롬프트에 <b>읽을 재료로만</b> 붙습니다.
 */
public final class YearlyMedians {

    /** 몇 해를 보여 줄까. 5년치를 받아 두므로 그만큼이다 */
    private static final int YEARS = 5;
    /** 이보다 적은 해는 중앙값이라 부를 수 없다. 건수를 함께 적어 그대로 보여 준다 */
    private static final int MIN_SAMPLES = 2;
    /** 해가 이만큼은 있어야 '모양'이라 할 수 있다 */
    private static final int MIN_YEARS = 3;

    private final TradeStatCalculator calculator = new TradeStatCalculator();

    /**
     * @return 예: {@code 2022년 5.95억 (12건) · 2023년 6.4억 (9건) · …}.
     *         해가 모자라면 <b>null</b> — 없는 모양을 지어내지 않는다
     */
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
            // 건수를 함께 적는다 — 두 건짜리 중앙값을 추세로 읽지 않게
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
