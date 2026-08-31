package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전세가율 지표 (설계 I131)")
class JeonseRatioIndicatorTest {

    private final JeonseRatioIndicator indicator =
            new JeonseRatioIndicator(new BigDecimal("0.70"), new BigDecimal("0.50"));

    @Test
    @DisplayName("70% 이상이고 안 내리면 UP — 실거주 수요가 하방을 받친다")
    void highAndNotFallingMeansUp() {
        // given — 매매 10억, 전세 7.5억 → 75%
        final var factor = indicator.evaluate(input(
                1_000_000_000L, 750_000_000L, 1_000_000_000L, 700_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("70.0%").contains("75.0%");
    }

    @Test
    @DisplayName("50% 미만이고 안 오르면 DOWN — 매매가에 기대가 실려 있다")
    void lowAndNotRisingMeansDown() {
        // given — 45% 로 떨어지는 중
        final var factor = indicator.evaluate(input(
                1_000_000_000L, 450_000_000L, 1_000_000_000L, 480_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("높은데 내리는 중이면 FLAT — 수준과 방향이 엇갈리면 단정하지 않는다")
    void highButFallingIsFlat() {
        // given — 80% 에서 72% 로. 여전히 높지만 내리는 중이다
        final var factor = indicator.evaluate(input(
                1_000_000_000L, 720_000_000L, 1_000_000_000L, 800_000_000L)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("중간대(50~70%)는 FLAT")
    void middleRangeIsFlat() {
        assertThat(indicator.evaluate(input(
                1_000_000_000L, 600_000_000L, 1_000_000_000L, 590_000_000L))
                .orElseThrow().effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("매매나 전세 어느 한쪽이 3건 미만이면 내지 않는다")
    void skipsWhenSamplesTooFew() {
        // given — 전세가 달마다 1건뿐 (6개월 × 1건 = 6건이지만 매매는 충분)
        final List<MonthlyTrades> sales = months(CachedDealType.TRADE, 1_000_000_000L, 3, 13);
        final List<MonthlyTrades> rents = months(CachedDealType.JEONSE, 700_000_000L, 0, 13);

        assertThat(indicator.evaluate(new ForecastInput(property(), sales, rents, List.of(), List.of(), null)))
                .isEmpty();
    }

    @Test
    @DisplayName("직전 구간이 없으면 수준만으로 판단하고 그 사실을 밝힌다")
    void worksWithoutPreviousWindow() {
        // given — 7개월치뿐이라 6개월 전 구간을 만들 수 없다
        final List<MonthlyTrades> sales = months(CachedDealType.TRADE, 1_000_000_000L, 3, 7);
        final List<MonthlyTrades> rents = months(CachedDealType.JEONSE, 750_000_000L, 3, 7);

        final var factor = indicator.evaluate(
                new ForecastInput(property(), sales, rents, List.of(), List.of(), null)).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("표본이 모자라 비교하지 않음");
    }

    // ── 도우미 ─────────────────────────────────────────────

    /** 최근 6개월과 그 앞 6개월의 매매·전세 금액을 정해 13개월치를 만든다(신고 지연 1달 포함). */
    private ForecastInput input(long recentSale, long recentRent, long oldSale, long oldRent) {
        final List<MonthlyTrades> sales = new ArrayList<>();
        final List<MonthlyTrades> rents = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            final YearMonth ym = YearMonth.now().minusMonths(12L - i);
            // 오래된 6개월(0~5) / 최근 6개월(6~11) / 신고 지연 달(12)
            final boolean old = i < 6;
            sales.add(month(ym, CachedDealType.TRADE, old ? oldSale : recentSale, 3));
            rents.add(month(ym, CachedDealType.JEONSE, old ? oldRent : recentRent, 3));
        }
        return new ForecastInput(property(), sales, rents, List.of(), List.of(), null);
    }

    private List<MonthlyTrades> months(CachedDealType type, long amount, int perMonth, int count) {
        final List<MonthlyTrades> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(month(YearMonth.now().minusMonths(count - 1L - i), type, amount, perMonth));
        }
        return list;
    }

    private MonthlyTrades month(YearMonth ym, CachedDealType type, long amount, int perMonth) {
        final List<ReferenceTrade> trades = new ArrayList<>();
        for (int j = 0; j < perMonth; j++) {
            trades.add(new ReferenceTrade("측정단지", amount, new BigDecimal("84.9"), 5, LocalDate.now()));
        }
        return new MonthlyTrades("11110", ym, type, trades, Instant.now());
    }

    private Property property() {
        return new Property(
                1L, "측정단지", null, DealType.SALE, 1_000_000_000L, null,
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
