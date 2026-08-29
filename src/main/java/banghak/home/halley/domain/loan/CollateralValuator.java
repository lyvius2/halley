package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 담보가치 추정 (설계 I64-1 · I65).
 *
 * <p><b>은행이 LTV를 매길 때 쓰는 것은 KB시세입니다.</b> 실측에서 호가 15억 / KB시세 13.5억으로
 * 1.5억 차이가 났습니다(설계 9.2). 호가를 담보가치로 쓰면 그 차이가 그대로 한도에 실립니다.
 *
 * <p>우선순위: KB시세 → 최근 실거래 → 공시가격 환산 → 호가. 어느 단계를 썼는지
 * {@link CollateralSource}로 남기고 화면에 표기합니다.
 *
 * <p><b>실거래는 금액을 그대로 평균 내지 않습니다</b>(설계 I65). 세 가지를 보정합니다.
 * <ul>
 *   <li><b>면적</b> — 단가(원/㎡)의 중앙값에 이 매물의 전용면적을 곱합니다. 같은 단지라도
 *       59㎡와 84㎡가 섞이면 금액 평균은 뜻이 없습니다.</li>
 *   <li><b>시점</b> — 최근 {@value #RECENT_MONTHS}개월 거래만 봅니다. 그 안에 거래가 없으면
 *       전체로 넓히되 건수를 함께 남겨 신뢰도를 낮춥니다.</li>
 *   <li><b>이상치</b> — 평균이 아니라 <b>중앙값</b>을 씁니다. 급매나 특수관계 거래 한 건이
 *       평균을 끌어내리기 때문입니다.</li>
 * </ul>
 *
 * <p>외부를 부르지 않는 순수 계산입니다 — 값은 호출자가 모아서 넘깁니다.
 */
public final class CollateralValuator {

    /** 이 기간 안의 거래를 우선 본다. 시세는 반년이면 꽤 움직인다. */
    public static final int RECENT_MONTHS = 6;

    private CollateralValuator() {
    }

    /**
     * @param kbPrice            KB시세(원). 있으면 무조건 이것
     * @param trades             동일 단지·유사 면적의 실거래 목록
     * @param exclusiveAreaM2    이 매물의 전용면적(㎡) — 단가 환산의 기준
     * @param officialPrice      공시가격(원)
     * @param askingPrice        호가(원)
     * @param officialPriceRatio 공시가격 현실화율 — 규제 파라미터
     * @param today              기준일. 최근성 판정에 쓴다
     */
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
        // 최근 거래를 우선 보되, 없으면 전체로 넓힌다 — 건수는 결과에 남겨 신뢰도를 드러낸다
        final LocalDate cutoff = today == null ? null : today.minusMonths(RECENT_MONTHS);
        final List<TradeSample> recent = cutoff == null ? valid : valid.stream()
                .filter(t -> t.contractDate() != null && !t.contractDate().isBefore(cutoff))
                .toList();
        final List<TradeSample> samples = recent.isEmpty() ? valid : recent;

        final Long byUnitPrice = medianUnitPrice(samples, exclusiveAreaM2);
        if (byUnitPrice != null) {
            return new CollateralValuation(byUnitPrice, CollateralSource.RECENT_TRADE, samples.size());
        }
        // 면적을 모르면 금액 중앙값으로 떨어진다
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
