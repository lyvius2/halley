package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.property.ComplexName;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 같은 단지·면적대의 거래 중앙값 (설계 I131).
 *
 * <p><b>지표들이 공유합니다.</b> 실거래 추세와 전세가율이 서로 다른 면적 기준을 쓰면
 * <b>다른 모집단을 비교하게 됩니다</b> — 전세가율은 특히 매매와 전세를 나누므로
 * 두 쪽 기준이 반드시 같아야 합니다.
 */
public class TradeStatCalculator {

    /**
     * 실거래 카드와 <b>같은 기준</b>(`ReferenceTransactionService.AREA_TOLERANCE`).
     * 두 화면이 다른 면적대를 보면 사용자가 헷갈립니다.
     */
    private static final BigDecimal AREA_TOLERANCE = new BigDecimal("0.15");
    /** 이보다 짧은 단지명은 우연히 걸린다 — 판정에 쓰지 않는다. */
    private static final int MIN_NAME_LENGTH = 2;

    /**
     * <b>위치가 아니라 연월로 자릅니다</b> (설계 I147).
     *
     * <p>예전에는 리스트 인덱스로 창을 잘랐습니다. 그런데 <b>못 받은 달은 목록에서
     * 빠집니다</b> — 60개 중 다섯 달이 없으면 "최근 3개월"이 실제로는 다른 달 셋을
     * 가리키고, 근거 문장에는 그대로 "직전 3개월"이라고 적힙니다.
     *
     * <p>구멍이 있으면 <b>표본이 줄어들 뿐</b>이어야 합니다. 그러면 표본 하한이
     * 알아서 판단을 보류합니다 — 조용히 다른 달을 보는 것보다 훨씬 낫습니다.
     *
     * @param base         "최근"의 기준 달. 목록의 마지막이 아니라 오늘이다
     * @param offsetMonths 몇 달 전 구간인지. 0이면 가장 최근
     * @param windowMonths 구간 길이
     * @param lagMonths    신고 지연으로 뺄 최근 달 수
     */
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

    /**
     * 창을 한 달씩 밀며 중앙값을 죽 뽑는다 (설계 I148).
     *
     * <p>전고점을 찾으려면 <b>한 달만 보면 안 됩니다.</b> 거래가 한두 건인 달은
     * 중앙값이 튀어 <b>실제로는 없던 고점</b>이 만들어집니다. 3개월씩 겹쳐 훑습니다.
     *
     * @param spanMonths 얼마나 거슬러 볼지. 이 기간 안에서 창을 민다
     * @return 표본이 {@code minSamples}에 못 미치는 창은 <b>빼고</b> 돌려준다
     */
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

    /**
     * <b>평균이 아니라 중앙값입니다.</b> 표본이 얇아 대형 평형 한 건이 섞이면
     * 평균은 통째로 끌려갑니다.
     */
    private BigDecimal median(List<Long> sorted) {
        final int n = sorted.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(n / 2));
        }
        return BigDecimal.valueOf(sorted.get(n / 2 - 1) + sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

    private boolean matches(Property property, ReferenceTrade trade) {
        return sameName(property, trade) && sameArea(property, trade);
    }

    /**
     * 표기가 흔들리므로(`래미안` vs `래미안아파트`) <b>서로 포함</b>이면 같게 본다.
     *
     * <p><b>규칙은 `ComplexName` 하나입니다 (설계 I230).</b> 여기서 따로 정규화하다가
     * 괄호 안을 남겨, `상계주공7(고층)` 이 `상계주공7단지` 와 안 맞았습니다 —
     * 그 단지는 전망이 <b>늘 자료 부족</b>이었습니다.
     */
    private boolean sameName(Property property, ReferenceTrade trade) {
        final String mine = property == null ? null : property.name();
        if (!ComplexName.comparable(mine, trade.apartmentName())) {
            // 이름을 못 가리면 면적으로만 본다 — 같은 법정동이라 아주 틀리진 않는다
            return true;
        }
        return ComplexName.same(mine, trade.apartmentName());
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
