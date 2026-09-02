package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거래가 드문 단지도 추세를 낸다 (설계 I252).
 *
 * <h4>왜 필요한가</h4>
 *
 * <p>345세대 단지의 한 평형은 1년에 8건쯤 팔립니다. 3개월 창에 3건은 채우기
 * 어렵고, 그래서 <b>작은 단지는 늘 판단 보류</b>였습니다 — 실제로 겪은 화면입니다.
 *
 * <pre>
 * 송산 58.59㎡ · 기준 2026-09 · 신고 지연 1개월
 *   최근 3개월 (2026-06~08) : 4건  ✅
 *   직전 3개월 (2026-03~05) : 2건  ❌  ← 여기서 끊겼다
 * </pre>
 *
 * <p>표본 기준을 낮추면 <b>2건짜리 중앙값</b>을 추세라 부르게 됩니다([I130]).
 * 기준 대신 <b>기간</b>을 늘립니다.
 */
@DisplayName("실거래 추세 — 창 넓히기 (설계 I252)")
class TradeTrendWideWindowTest {

    private static final YearMonth BASE = YearMonth.of(2026, 9);
    private final TradeTrendIndicator indicator = new TradeTrendIndicator(new BigDecimal("0.02"));

    /**
     * <b>실제로 겪은 화면입니다.</b> 직전 3개월이 2건이라 끊겼는데,
     * 6개월로 넓히면 양쪽 다 찹니다.
     */
    @Test
    @DisplayName("3개월로 안 되면 6개월로 넓혀 낸다")
    void widensWhenThreeMonthsIsTooThin() {
        final List<Trade> trades = List.of(
                // 최근 3개월 (1~3달 전) — 3건
                new Trade(1, 670_000_000L), new Trade(2, 720_000_000L), new Trade(2, 710_000_000L),
                // 직전 3개월 (4~6달 전) — 2건뿐. 여기서 끊긴다
                new Trade(4, 660_000_000L), new Trade(6, 605_000_000L),
                // 그 앞 (7~12달 전) — 6개월로 넓히면 양쪽 다 찬다
                new Trade(7, 600_000_000L), new Trade(9, 610_000_000L), new Trade(11, 590_000_000L));

        final Optional<PriceFactor> factor = indicator.evaluate(input(trades));

        assertThat(factor).as("3개월로 끊기면 작은 단지는 영영 추세가 없다").isPresent();
        assertThat(factor.get().evidence())
                .as("넓혔다는 사실을 말해야 한다 — 3개월 값인 줄 알면 오해한다")
                .contains("6개월")
                .contains("거래가 드물어 창을 넓혀 쟀습니다");
    }

    /**
     * <b>좁을수록 최근을 잘 비춥니다.</b> 3개월로 되면 굳이 넓히지 않습니다.
     */
    @Test
    @DisplayName("3개월로 되면 넓히지 않는다")
    void keepsTheNarrowWindowWhenItFits() {
        final List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            trades.add(new Trade(2, 700_000_000L));   // 최근 3개월
            trades.add(new Trade(5, 650_000_000L));   // 직전 3개월
        }

        final Optional<PriceFactor> factor = indicator.evaluate(input(trades));

        assertThat(factor).isPresent();
        assertThat(factor.get().evidence())
                .contains("3개월")
                .doesNotContain("넓혀");
        assertThat(factor.get().effect()).isEqualTo(ForecastDirection.UP);
    }

    /**
     * 넓혀도 안 되면 <b>내지 않습니다.</b> [I130]의 취지는 그대로입니다 —
     * 억지로 방향을 주면 없는 신호를 만듭니다.
     */
    @Test
    @DisplayName("6개월로 넓혀도 표본이 얇으면 안 낸다")
    void staysSilentWhenEvenTheWideWindowIsThin() {
        final List<Trade> trades = List.of(
                new Trade(1, 670_000_000L), new Trade(2, 720_000_000L), new Trade(3, 700_000_000L),
                // 직전 6개월(7~12달 전)에 2건뿐
                new Trade(8, 660_000_000L), new Trade(10, 705_000_000L));

        assertThat(indicator.evaluate(input(trades))).isEmpty();
    }

    // ── 도우미 ─────────────────────────────────────────────

    private record Trade(int monthsAgo, long amount) {
    }

    /** 기준달로부터 {@code monthsAgo} 달 전에 거래 한 건을 둔다. */
    private ForecastInput input(List<Trade> trades) {
        final Map<YearMonth, List<ReferenceTrade>> byMonth = new LinkedHashMap<>();
        for (final Trade t : trades) {
            byMonth.computeIfAbsent(BASE.minusMonths(t.monthsAgo()), m -> new ArrayList<>())
                    .add(new ReferenceTrade("측정단지", t.amount(), new BigDecimal("84.9"), 5,
                            LocalDate.now()));
        }
        final List<MonthlyTrades> months = new ArrayList<>();
        byMonth.forEach((ym, list) ->
                months.add(new MonthlyTrades("11110", ym, CachedDealType.TRADE, list, Instant.now())));
        return new ForecastInput(property(), months, List.of(), List.of(), List.of(), null, BASE);
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
