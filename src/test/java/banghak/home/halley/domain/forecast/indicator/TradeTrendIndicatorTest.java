package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("실거래 추세 지표 (설계 I130)")
class TradeTrendIndicatorTest {

    private final TradeTrendIndicator indicator = new TradeTrendIndicator(new BigDecimal("0.02"));

    @Test
    @DisplayName("직전 3개월보다 오르면 UP")
    void risesWhenRecentMedianIsHigher() {
        // given — 오래된 달부터: [직전 3개월 10억] [최근 3개월 11억] [신고 지연 달]
        final var input = input(
                months(3, 1_000_000_000L),
                months(3, 1_100_000_000L),
                months(1, 1_100_000_000L));

        // when
        final PriceFactor factor = indicator.evaluate(input).orElseThrow();

        // then
        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("+10.0%").contains("10억원").contains("11억원");
    }

    @Test
    @DisplayName("가장 최근 달은 계산에서 뺀다 — 국토부 신고가 덜 들어와 있다")
    void excludesTheReportingLagMonth() {
        // given — 지연 달에 '한 구간을 뒤집을 만큼' 거래를 넣는다.
        // 몇 건만 넣으면 중앙값이 튼튼해서 포함되든 말든 결과가 같아 검증이 안 된다
        final List<MonthlyTrades> monthly = new ArrayList<>();
        monthly.addAll(monthsWithCount(3, 1_000_000_000L, 3));   // 직전
        monthly.addAll(monthsWithCount(3, 1_000_000_000L, 3));   // 최근 — 같은 값이라 FLAT
        monthly.add(new MonthlyTrades("11110", YearMonth.now(),
                java.util.Collections.nCopies(20, trade("측정단지", 5_000_000_000L, "84.9")),
                Instant.now()));

        // 오래된 달부터로 다시 매긴다
        final List<MonthlyTrades> ordered = new ArrayList<>();
        for (int i = 0; i < monthly.size(); i++) {
            ordered.add(new MonthlyTrades("11110", YearMonth.now().minusMonths(monthly.size() - 1L - i),
                    monthly.get(i).trades(), Instant.now()));
        }

        // when
        final PriceFactor factor = indicator.evaluate(
                new ForecastInput(property("측정단지", "84.9"), ordered)).orElseThrow();

        // then — 지연 달(50억 × 20건)이 최근 구간에 들어오면 UP 이 된다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("표본 9건 → 9건");
    }

    @Test
    @DisplayName("내리면 DOWN")
    void fallsWhenRecentMedianIsLower() {
        final var input = input(
                months(3, 1_210_000_000L),
                months(3, 1_140_000_000L),
                months(1, 1_140_000_000L));

        assertThat(indicator.evaluate(input).orElseThrow().effect())
                .isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("임계값 안이면 FLAT — 잡음을 방향으로 읽지 않는다")
    void flatWithinThreshold() {
        // given — 1% 상승. 임계값 2% 안이다
        final var input = input(
                months(3, 1_000_000_000L),
                months(3, 1_010_000_000L),
                months(1, 1_010_000_000L));

        assertThat(indicator.evaluate(input).orElseThrow().effect())
                .isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("표본이 3건 미만이면 판단하지 않는다 — 억지로 방향을 주면 없는 신호를 만든다")
    void skipsWhenSamplesTooFew() {
        // given — 최근 구간에 2건뿐
        final List<MonthlyTrades> monthly = new ArrayList<>(months(3, 1_000_000_000L));
        monthly.addAll(monthsWithCount(3, 1_100_000_000L, 0));
        monthly.set(3, one(monthly.get(3).dealYm(), 1_100_000_000L));
        monthly.set(4, one(monthly.get(4).dealYm(), 1_100_000_000L));
        monthly.addAll(months(1, 1_100_000_000L));

        assertThat(indicator.evaluate(new ForecastInput(property("측정단지", "84.9"), monthly)))
                .isEmpty();
    }

    @Test
    @DisplayName("달이 모자라면 판단하지 않는다")
    void skipsWhenNotEnoughMonths() {
        assertThat(indicator.evaluate(input(months(4, 1_000_000_000L)))).isEmpty();
        assertThat(indicator.evaluate(new ForecastInput(property("단지", "84.9"), List.of()))).isEmpty();
        assertThat(indicator.evaluate(new ForecastInput(property("단지", "84.9"), null))).isEmpty();
    }

    @Test
    @DisplayName("면적대가 다른 거래는 세지 않는다 — 84㎡와 115㎡는 다른 시장이다")
    void ignoresDifferentAreas() {
        // given — 최근 구간에 큰 평형 거래를 84㎡와 '같은 수'만큼 넣는다.
        // 몇 건만 섞으면 중앙값이 튼튼해서 걸러지든 말든 결과가 같아 검증이 안 된다
        final List<MonthlyTrades> monthly = new ArrayList<>(months(3, 1_000_000_000L));
        for (int i = 0; i < 3; i++) {
            final YearMonth ym = YearMonth.now().minusMonths(3L - i);
            monthly.add(new MonthlyTrades("11110", ym, List.of(
                    trade("측정단지", 1_000_000_000L, "84.9"),
                    trade("측정단지", 1_000_000_000L, "84.9"),
                    trade("측정단지", 1_000_000_000L, "84.9"),
                    trade("측정단지", 3_000_000_000L, "115.5"),   // ← 셋 다 걸러져야 한다
                    trade("측정단지", 3_000_000_000L, "115.5"),
                    trade("측정단지", 3_000_000_000L, "115.5")),
                    Instant.now()));
        }
        monthly.addAll(months(1, 1_000_000_000L));

        // when
        final PriceFactor factor = indicator.evaluate(
                new ForecastInput(property("측정단지", "84.9"), monthly)).orElseThrow();

        // then — 안 걸러지면 중앙값이 20억이 되어 UP 이 나온다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("표본 9건 → 9건");
    }

    @Test
    @DisplayName("단지명 표기가 흔들려도 같게 본다 — '래미안' vs '래미안아파트'")
    void toleratesNameVariants() {
        final List<MonthlyTrades> monthly = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final YearMonth ym = YearMonth.now().minusMonths(6L - i);
            monthly.add(new MonthlyTrades("11110", ym, List.of(
                    trade("래미안 아파트", i < 3 ? 1_000_000_000L : 1_100_000_000L, "84.9"),
                    trade("래미안아파트", i < 3 ? 1_000_000_000L : 1_100_000_000L, "84.9"),
                    trade("래미안", i < 3 ? 1_000_000_000L : 1_100_000_000L, "84.9")),
                    Instant.now()));
        }
        monthly.addAll(months(1, 1_100_000_000L));

        final PriceFactor factor = indicator.evaluate(
                new ForecastInput(property("래미안", "84.9"), monthly)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("표본 9건 → 9건");
    }

    @Test
    @DisplayName("중앙값을 쓴다 — 평균이면 이상치 한 건에 끌려간다")
    void usesMedianNotMean() {
        // given — 직전 구간에 30억 한 건. 평균이면 크게 뛰지만 중앙값은 안 움직인다
        final List<MonthlyTrades> monthly = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            monthly.add(new MonthlyTrades("11110", YearMonth.now().minusMonths(6L - i), List.of(
                    trade("측정단지", 1_000_000_000L, "84.9"),
                    trade("측정단지", 1_000_000_000L, "84.9"),
                    trade("측정단지", 1_000_000_000L, "84.9")), Instant.now()));
        }
        monthly.get(0).trades();
        monthly.set(0, new MonthlyTrades("11110", monthly.get(0).dealYm(), List.of(
                trade("측정단지", 1_000_000_000L, "84.9"),
                trade("측정단지", 1_000_000_000L, "84.9"),
                trade("측정단지", 3_000_000_000L, "84.9")), Instant.now()));
        monthly.addAll(months(3, 1_000_000_000L));
        monthly.addAll(months(1, 1_000_000_000L));

        // when
        final PriceFactor factor = indicator.evaluate(
                new ForecastInput(property("측정단지", "84.9"), monthly)).orElseThrow();

        // then — 평균이었다면 직전 중앙값이 뛰어 DOWN 이 나온다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("근거에 표본 수를 남긴다 — 3건으로 낸 판단과 30건은 다르다")
    void evidenceCarriesSampleCount() {
        final PriceFactor factor = indicator.evaluate(input(
                months(3, 1_000_000_000L), months(3, 1_100_000_000L), months(1, 1_100_000_000L)))
                .orElseThrow();

        assertThat(factor.evidence()).contains("표본 9건 → 9건");
        assertThat(factor.weight()).isEqualTo(banghak.home.halley.domain.forecast.FactorWeight.HIGH);
    }

    // ── 도우미 ─────────────────────────────────────────────

    @SafeVarargs
    private ForecastInput input(List<MonthlyTrades>... parts) {
        final List<MonthlyTrades> all = new ArrayList<>();
        for (final List<MonthlyTrades> part : parts) {
            all.addAll(part);
        }
        // 오래된 달부터가 되도록 달을 다시 매긴다
        final List<MonthlyTrades> ordered = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            final YearMonth ym = YearMonth.now().minusMonths(all.size() - 1L - i);
            ordered.add(new MonthlyTrades("11110", ym, all.get(i).trades(), Instant.now()));
        }
        return new ForecastInput(property("측정단지", "84.9"), ordered);
    }

    /** 달마다 같은 가격 3건씩. */
    private List<MonthlyTrades> months(int count, long price) {
        return monthsWithCount(count, price, 3);
    }

    private List<MonthlyTrades> monthsWithCount(int count, long price, int perMonth) {
        final List<MonthlyTrades> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final List<ReferenceTrade> trades = new ArrayList<>();
            for (int j = 0; j < perMonth; j++) {
                trades.add(trade("측정단지", price, "84.9"));
            }
            list.add(new MonthlyTrades("11110", YearMonth.now().minusMonths(count - (long) i), trades,
                    Instant.now()));
        }
        return list;
    }

    private MonthlyTrades one(YearMonth ym, long price) {
        return new MonthlyTrades("11110", ym, List.of(trade("측정단지", price, "84.9")), Instant.now());
    }

    private ReferenceTrade trade(String name, long price, String area) {
        return new ReferenceTrade(name, price, new BigDecimal(area), 5, LocalDate.now());
    }

    private Property property(String name, String areaM2) {
        return new Property(
                1L, name, null, DealType.SALE, 1_120_000_000L, null,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal(areaM2), null, 5, 15, null, null, null,
                2018, null, null, null, 300, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
