package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.TradeStat;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
     * @param offsetMonths 몇 달 전 구간인지. 0이면 가장 최근
     * @param windowMonths 구간 길이
     * @param lagMonths    신고 지연으로 뺄 최근 달 수
     */
    public TradeStat medianOf(Property property, List<MonthlyTrades> monthly,
                              int offsetMonths, int windowMonths, int lagMonths) {
        if (monthly == null || monthly.isEmpty()) {
            return new TradeStat(null, 0);
        }
        final int end = monthly.size() - lagMonths - offsetMonths;
        final int start = end - windowMonths;
        if (end <= 0 || start < 0) {
            return new TradeStat(null, 0);
        }
        return statOf(property, monthly.subList(start, end));
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

    /** 표기가 흔들리므로(`래미안` vs `래미안아파트`) <b>서로 포함</b>이면 같게 본다. */
    private boolean sameName(Property property, ReferenceTrade trade) {
        final String mine = normalize(property == null ? null : property.name());
        final String theirs = normalize(trade.apartmentName());
        if (mine == null || theirs == null || mine.length() < MIN_NAME_LENGTH) {
            // 이름을 못 가리면 면적으로만 본다 — 같은 법정동이라 아주 틀리진 않는다
            return true;
        }
        return mine.contains(theirs) || theirs.contains(mine);
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("[\\s()·\\-]", "").replace("아파트", "");
    }
}
