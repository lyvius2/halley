package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 담보가치 추정. */
public final class CollateralValuator {

 /** 이 기간 안의 거래를 우선 본다. 시세는 반년이면 꽤 움직인다. */
    public static final int RECENT_MONTHS = 6;

    private CollateralValuator() {
    }


    public static CollateralValuation estimate(Long kbPrice,
                                               List<TradeSample> trades,
                                               BigDecimal exclusiveAreaM2,
                                               Long officialPrice,
                                               Long askingPrice,
                                               BigDecimal officialPriceRatio,
                                               LocalDate today) {
        if (isPositive(kbPrice)) {
            return CollateralValuation.of(kbPrice, CollateralSource.KB_PRICE);
        }
        final CollateralValuation fromTrades = fromTrades(trades, exclusiveAreaM2, today);
        if (fromTrades != null) {
            return fromTrades;
        }
        if (isPositive(officialPrice) && officialPriceRatio != null
                && officialPriceRatio.compareTo(BigDecimal.ZERO) > 0) {
            final long converted = BigDecimal.valueOf(officialPrice)
                    .divide(officialPriceRatio, 0, RoundingMode.HALF_UP)
                    .longValue();
            return CollateralValuation.of(converted, CollateralSource.OFFICIAL_PRICE);
        }
        return CollateralValuation.of(isPositive(askingPrice) ? askingPrice : 0L,
                CollateralSource.ASKING_PRICE);
    }

    private static CollateralValuation fromTrades(List<TradeSample> trades,
                                                  BigDecimal exclusiveAreaM2,
                                                  LocalDate today) {
        if (trades == null || trades.isEmpty()) {
            return null;
        }
        final List<TradeSample> valid = trades.stream()
                .filter(t -> t != null && t.price() > 0)
                .toList();
        if (valid.isEmpty()) {
            return null;
        }
        final LocalDate cutoff = today == null ? null : today.minusMonths(RECENT_MONTHS);
        final List<TradeSample> recent = cutoff == null ? valid : valid.stream()
                .filter(t -> t.contractDate() != null && !t.contractDate().isBefore(cutoff))
                .toList();
        final List<TradeSample> samples = recent.isEmpty() ? valid : recent;

        final Long byUnitPrice = medianUnitPrice(samples, exclusiveAreaM2);
        if (byUnitPrice != null) {
            return new CollateralValuation(byUnitPrice, CollateralSource.RECENT_TRADE, samples.size());
        }
        final Long median = median(samples.stream().map(TradeSample::price).toList());
        return median == null
                ? null
                : new CollateralValuation(median, CollateralSource.RECENT_TRADE, samples.size());
    }

 /** 단가(원/㎡) 중앙값 × 이 매물의 전용면적. 면적을 모르는 거래는 빠진다. */
    private static Long medianUnitPrice(List<TradeSample> samples, BigDecimal exclusiveAreaM2) {
        if (exclusiveAreaM2 == null || exclusiveAreaM2.signum() <= 0) {
            return null;
        }
        final List<Long> unitPrices = samples.stream()
                .filter(t -> t.areaM2() != null && t.areaM2().signum() > 0)
                .map(t -> BigDecimal.valueOf(t.price())
                        .divide(t.areaM2(), 0, RoundingMode.HALF_UP)
                        .longValue())
                .toList();
        final Long medianUnit = median(unitPrices);
        if (medianUnit == null) {
            return null;
        }
        return BigDecimal.valueOf(medianUnit)
                .multiply(exclusiveAreaM2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static Long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final List<Long> sorted = values.stream()
                .filter(v -> v != null && v > 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        final int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(mid)
                : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }
}
