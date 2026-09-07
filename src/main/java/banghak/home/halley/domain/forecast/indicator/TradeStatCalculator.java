package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.property.ComplexMatch;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 같은 단지·면적대의 거래 중앙값. */
public class TradeStatCalculator {

 /** 실거래 카드와 같은 기준(ReferenceTransactionService.AREA_TOLERANCE). */
    private static final BigDecimal AREA_TOLERANCE = new BigDecimal("0.15");
 /** 이보다 짧은 단지명은 우연히 걸린다. 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

 /** 위치가 아니라 연월로 자릅니다. */
    public TradeStat medianOf(Property property, List<MonthlyTrades> monthly, YearMonth base,
                              int offsetMonths, int windowMonths, int lagMonths) {
        if (monthly == null || monthly.isEmpty() || base == null || windowMonths <= 0) {
            return new TradeStat(null, 0);
        }
        final YearMonth newest = base.minusMonths((long) lagMonths + offsetMonths);
        final YearMonth oldest = newest.minusMonths(windowMonths - 1L);
        return statOf(property, monthly.stream()
                .filter(m -> m != null && m.dealYm() != null
                        && !m.dealYm().isBefore(oldest) && !m.dealYm().isAfter(newest))
                .toList());
    }

 /** 창을 한 달씩 밀며 중앙값을 죽 뽑는다. */
    public List<TradeStat> rollingMedians(Property property, List<MonthlyTrades> monthly, YearMonth base,
                                          int spanMonths, int windowMonths, int lagMonths, int minSamples) {
        final List<TradeStat> stats = new ArrayList<>();
        if (monthly == null || monthly.isEmpty() || base == null || windowMonths <= 0) {
            return stats;
        }
        for (int offset = 0; offset + windowMonths <= spanMonths; offset++) {
            final TradeStat stat = medianOf(property, monthly, base, offset, windowMonths, lagMonths);
            if (stat.median() != null && stat.count() >= minSamples) {
                stats.add(stat);
            }
        }
        return stats;
    }

    private TradeStat statOf(Property property, List<MonthlyTrades> window) {
        final List<Long> amounts = new ArrayList<>();
        for (final MonthlyTrades month : window) {
            if (month == null || month.trades() == null) {
                continue;
            }
            for (final ReferenceTrade trade : month.trades()) {
                if (trade.dealAmount() != null && matches(property, trade)) {
                    amounts.add(trade.dealAmount());
                }
            }
        }
        if (amounts.isEmpty()) {
            return new TradeStat(null, 0);
        }
        amounts.sort(Comparator.naturalOrder());
        return new TradeStat(median(amounts), amounts.size());
    }

 /** 평균이 아니라 중앙값입니다. 표본이 얇아 대형 평형 한 건이 섞이면 */
    private BigDecimal median(List<Long> sorted) {
        final int n = sorted.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(n / 2));
        }
        return BigDecimal.valueOf(sorted.get(n / 2 - 1) + sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

 /** 어디서 0이 됐는지 센다. */
    public MatchTally tally(Property property, List<MonthlyTrades> monthly) {
        int trades = 0;
        int nameMatched = 0;
        int areaMatched = 0;
        if (monthly != null) {
            for (final MonthlyTrades month : monthly) {
                if (month == null || month.trades() == null) {
                    continue;
                }
                for (final ReferenceTrade trade : month.trades()) {
                    trades++;
                    if (!sameName(property, trade)) {
                        continue;
                    }
                    nameMatched++;
                    if (sameArea(property, trade)) {
                        areaMatched++;
                    }
                }
            }
        }
        return new MatchTally(trades, nameMatched, areaMatched);
    }

 /** 창 안의 거래가 어디서 걸러졌는가. */
    public record MatchTally(int trades, int nameMatched, int areaMatched) {
    }

    private boolean matches(Property property, ReferenceTrade trade) {
        return matchesProperty(property, trade);
    }

 /** 이 거래가 이 매물의 것인가. */
    public boolean matchesProperty(Property property, ReferenceTrade trade) {
        return sameName(property, trade) && sameArea(property, trade);
    }

 /** 같은 단지인가. */
    private boolean sameName(Property property, ReferenceTrade trade) {
        return ComplexMatch.same(
                property == null ? null : property.addressJibun(),
                property == null ? null : property.name(),
                trade);
    }

    private boolean sameArea(Property property, ReferenceTrade trade) {
        final BigDecimal mine = property == null ? null : property.areaExclusiveM2();
        final BigDecimal theirs = trade.areaM2();
        if (mine == null || theirs == null || mine.signum() <= 0) {
            return true;
        }
        return theirs.subtract(mine).abs()
                .divide(mine, 6, RoundingMode.HALF_UP)
                .compareTo(AREA_TOLERANCE) <= 0;
    }

}
