package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.property.ReferenceTrade;
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

@DisplayName("장기 추세 지표 (설계 I148)")
class LongTermTrendIndicatorTest {

    private final LongTermTrendIndicator indicator =
            new LongTermTrendIndicator(new BigDecimal("0.02"));

    @Test
    @DisplayName("4년에 걸쳐 오르면 UP — 연평균으로 환산한다")
    void risesOverFourYears() {
        // given — 오래된 12개월 10억, 최근 12개월 12억 (총 +20%, 연 +5%)
        final PriceFactor factor = indicator.evaluate(input(
                m -> m >= 49 ? 1_000_000_000L : 1_200_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.weight()).isEqualTo(FactorWeight.MEDIUM);
        assertThat(factor.evidence()).contains("+20.0%").contains("연 +5.0%");
    }

    @Test
    @DisplayName("4년에 걸쳐 내리면 DOWN")
    void fallsOverFourYears() {
        final PriceFactor factor = indicator.evaluate(input(
                m -> m >= 49 ? 1_200_000_000L : 1_000_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("연 2% 안이면 FLAT — 물가 언저리 움직임을 방향으로 읽지 않는다")
    void smallDriftIsFlat() {
        // 총 +4% → 연 +1%
        final PriceFactor factor = indicator.evaluate(input(
                m -> m >= 49 ? 1_000_000_000L : 1_040_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("단기와 어긋나도 그대로 낸다 — 그 어긋남이 이 지표의 값어치다")
    void keepsDivergenceFromShortTerm() {
        // given — 4년 동안 크게 올랐지만 최근 몇 달은 빠졌다.
        // 최근 12개월 창 안의 마지막 석 달만 떨어뜨린다
        final PriceFactor factor = indicator.evaluate(input(m -> {
            if (m >= 49) return 1_000_000_000L;      // 4년 전
            if (m <= 4) return 1_150_000_000L;        // 최근 몇 달 — 조정
            return 1_300_000_000L;
        })).orElseThrow();

        // 최근 12개월 중앙값은 13억 쪽이라 여전히 UP 이다. 조정은 실거래 추세가 잡는다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
    }

    @Test
    @DisplayName("오래된 쪽 자료가 없으면 내지 않는다 — 없는 기준으로 견주지 않는다")
    void needsBothEnds() {
        // 최근 24개월치만 있다
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 24; m >= 1; m--) {
            months.add(month(m, 1_000_000_000L, 3));
        }
        assertThat(indicator.evaluate(ForecastInput.ofTrades(property(), months))).isEmpty();
    }

    @Test
    @DisplayName("한쪽 표본이 3건 미만이면 내지 않는다 — 중앙값은 나오지만 믿을 수 없다")
    void needsEnoughSamples() {
        // 오래된 쪽 창(49~60개월 전) 전체에 2건만 둔다.
        // 0건으로 두면 '중앙값 없음'에서 먼저 걸려 표본 하한이 검증되지 않는다
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 61; m >= 1; m--) {
            final int count = m >= 49 ? (m == 49 ? 2 : 0) : 3;
            months.add(month(m, 1_000_000_000L, count));
        }
        assertThat(indicator.evaluate(ForecastInput.ofTrades(property(), months))).isEmpty();
    }

    // ── 도우미 ─────────────────────────────────────────────

    /** 61개월치를 만든다. {@code priceAt}은 '몇 개월 전'을 받아 가격을 준다. */
    private ForecastInput input(IntToLongFunction priceAt) {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 61; m >= 1; m--) {
            months.add(month(m, priceAt.applyAsLong(m), 3));
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
