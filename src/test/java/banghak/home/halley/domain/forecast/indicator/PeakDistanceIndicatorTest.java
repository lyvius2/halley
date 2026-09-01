package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntToLongFunction;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전고점 대비 지표 (설계 I148)")
class PeakDistanceIndicatorTest {

    private final PeakDistanceIndicator indicator =
            new PeakDistanceIndicator(new BigDecimal("0.95"), new BigDecimal("0.80"));

    @Test
    @DisplayName("고점에서 많이 내려와 있으면 UP — 올라갈 여유가 있다고 본다")
    void farBelowPeakIsUp() {
        // given — 3년 전에 13억까지 갔다가 지금 10억
        final PriceFactor factor = indicator.evaluate(input(m -> {
            if (m >= 34 && m <= 39) return 1_300_000_000L;   // 전고점
            return 1_000_000_000L;
        })).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.weight()).isEqualTo(FactorWeight.MEDIUM);
        assertThat(factor.evidence()).contains("13억원").contains("76.9%");
    }

    @Test
    @DisplayName("고점에 붙어 있으면 DOWN — 더 오를 여력이 적다고 본다 (평균회귀 가정)")
    void nearPeakIsDown() {
        // 줄곧 10억이면 지금이 곧 고점이다
        final PriceFactor factor = indicator.evaluate(input(m -> 1_000_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.DOWN);
        assertThat(factor.evidence()).contains("100.0%");
    }

    @Test
    @DisplayName("그 사이면 FLAT")
    void inBetweenIsFlat() {
        // 고점 11.5억, 현재 10억 → 87%
        final PriceFactor factor = indicator.evaluate(input(m -> {
            if (m >= 34 && m <= 39) return 1_150_000_000L;
            return 1_000_000_000L;
        })).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
    }

    /**
     * <b>이것이 이 지표를 만든 이유다.</b> 실거래 추세(모멘텀)와 반대 방향을 낼 수 있어야 한다 —
     * 저울질은 LLM 이 한다. 여기서 합치면 그 재료가 사라진다.
     */
    @Test
    @DisplayName("오르는 중이어도 고점 근처면 DOWN을 낸다 — 모멘텀과 어긋나는 것이 이 지표의 몫이다")
    void disagreesWithMomentumNearThePeak() {
        // given — 5년 내내 오르는 중. 지금이 최고점이다
        final PriceFactor factor = indicator.evaluate(
                input(m -> 900_000_000L + (61L - m) * 5_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("비교할 구간이 12개 미만이면 내지 않는다 — 서넛 중 최고는 고점이 아니다")
    void needsEnoughWindowsToCallItAPeak() {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 10; m >= 1; m--) {
            months.add(month(m, 1_000_000_000L, 3));
        }
        assertThat(indicator.evaluate(ForecastInput.ofTrades(property(), months))).isEmpty();
    }

    @Test
    @DisplayName("최근 표본이 3건 미만이면 내지 않는다")
    void needsRecentSamples() {
        // 최근 구간(1·2·3개월 전)에 1 + 1 + 0 = 2건만 둔다. 3건이면 통과해 버린다
        assertThat(indicator.evaluate(input(
                m -> 1_000_000_000L, m -> m == 3 ? 0 : (m <= 2 ? 1 : 3)))).isEmpty();
    }

    // ── 도우미 ─────────────────────────────────────────────

    /** 61개월치를 만든다. {@code priceAt}은 '몇 개월 전'을 받아 가격을 준다. */
    private ForecastInput input(IntToLongFunction priceAt) {
        return input(priceAt, m -> 3);
    }

    private ForecastInput input(IntToLongFunction priceAt, java.util.function.IntUnaryOperator countAt) {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 61; m >= 1; m--) {
            months.add(month(m, priceAt.applyAsLong(m), countAt.applyAsInt(m)));
        }
        return ForecastInput.ofTrades(property(), months);
    }

    private MonthlyTrades month(int monthsAgo, long price, int count) {
        return new MonthlyTrades("11110", YearMonth.now().minusMonths(monthsAgo), CachedDealType.TRADE,
                Collections.nCopies(count, new ReferenceTrade(
                        "측정단지", price, new BigDecimal("84.9"), 5, LocalDate.now())),
                Instant.now());
    }

    private Property property() {
        return new Property(
                1L, "측정단지", null, DealType.SALE, 1_120_000_000L, null,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), null, 5, 15, null, null, null,
                2018, null, null, null, 300, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
