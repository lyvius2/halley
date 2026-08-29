package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("담보가치 추정 (설계 I64-1 · I65)")
class CollateralValuatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);
    private static final BigDecimal RATIO = new BigDecimal("0.7");
    private static final BigDecimal AREA = new BigDecimal("84.90");

    @Test
    @DisplayName("KB시세가 있으면 무조건 그것을 쓴다 — 은행이 LTV에 쓰는 값이다")
    void kbPriceWinsOverEverything() {
        // given — 호가는 15억인데 KB시세는 13.5억 (설계 9.2 실측 사례)
        final CollateralValuation result = CollateralValuator.estimate(
                1_350_000_000L,
                List.of(trade(1_500_000_000L, AREA, TODAY.minusMonths(1))),
                AREA, 900_000_000L, 1_500_000_000L, RATIO, TODAY);

        // then
        assertThat(result.value()).isEqualTo(1_350_000_000L);
        assertThat(result.source()).isEqualTo(CollateralSource.KB_PRICE);
        assertThat(result.isReliable()).isTrue();
    }

    @Test
    @DisplayName("실거래는 금액이 아니라 단가(원/㎡) 중앙값에 매물 면적을 곱한다")
    void usesUnitPriceNotRawAmount() {
        // given — 같은 단지의 59㎡ 두 건과 84.9㎡ 한 건. 금액 평균이면 84.9㎡ 가치가 낮게 나온다
        final List<TradeSample> trades = List.of(
                trade(700_000_000L, new BigDecimal("59.00"), TODAY.minusMonths(1)),   // 단가 1,186만
                trade(720_000_000L, new BigDecimal("59.00"), TODAY.minusMonths(2)),   // 단가 1,220만
                trade(1_020_000_000L, new BigDecimal("84.90"), TODAY.minusMonths(3))); // 단가 1,201만

        // when
        final CollateralValuation result = CollateralValuator.estimate(
                null, trades, AREA, null, 1_100_000_000L, RATIO, TODAY);

        // then — 단가 중앙값 12,014,134원/㎡ × 84.9㎡ ≈ 10.2억.
        // 금액 중앙값(7.2억)을 그대로 썼다면 3억이 낮게 나왔을 것이다
        assertThat(result.source()).isEqualTo(CollateralSource.RECENT_TRADE);
        assertThat(result.value()).isBetween(1_000_000_000L, 1_040_000_000L);
        assertThat(result.sampleCount()).isEqualTo(3);
        assertThat(result.isReliable()).isTrue();
    }

    @Test
    @DisplayName("최근 6개월 거래만 본다 — 시세는 반년이면 움직인다")
    void prefersRecentTrades() {
        // given — 최근 거래는 비싸고, 2년 전 거래는 싸다
        final List<TradeSample> trades = List.of(
                trade(1_200_000_000L, AREA, TODAY.minusMonths(1)),
                trade(1_180_000_000L, AREA, TODAY.minusMonths(3)),
                trade(700_000_000L, AREA, TODAY.minusYears(2)),
                trade(680_000_000L, AREA, TODAY.minusYears(3)));

        // when
        final CollateralValuation result = CollateralValuator.estimate(
                null, trades, AREA, null, 1_300_000_000L, RATIO, TODAY);

        // then — 오래된 두 건은 빠져 2건만 남는다
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.value()).isBetween(1_150_000_000L, 1_220_000_000L);
    }

    @Test
    @DisplayName("최근 거래가 없으면 전체로 넓히되 신뢰도를 낮춘다")
    void widensWhenNoRecentTrade() {
        // given — 전부 2년 전 거래 두 건
        final List<TradeSample> trades = List.of(
                trade(700_000_000L, AREA, TODAY.minusYears(2)),
                trade(720_000_000L, AREA, TODAY.minusYears(2).minusMonths(1)));

        // when
        final CollateralValuation result = CollateralValuator.estimate(
                null, trades, AREA, null, 900_000_000L, RATIO, TODAY);

        // then — 값은 나오지만 표본이 3건 미만이라 신뢰할 만하지 않다
        assertThat(result.source()).isEqualTo(CollateralSource.RECENT_TRADE);
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.isReliable()).isFalse();
    }

    @Test
    @DisplayName("중앙값을 써서 급매 한 건이 값을 끌어내리지 못하게 한다")
    void medianResistsOutlier() {
        // given — 한 건만 유난히 싸다
        final List<TradeSample> trades = List.of(
                trade(1_200_000_000L, AREA, TODAY.minusMonths(1)),
                trade(1_210_000_000L, AREA, TODAY.minusMonths(2)),
                trade(1_190_000_000L, AREA, TODAY.minusMonths(3)),
                trade(600_000_000L, AREA, TODAY.minusMonths(4)));

        // when
        final CollateralValuation result = CollateralValuator.estimate(
                null, trades, AREA, null, 1_300_000_000L, RATIO, TODAY);

        // then — 평균이었다면 10.5억으로 내려갔을 것이다
        assertThat(result.value()).isBetween(1_180_000_000L, 1_210_000_000L);
    }

    @Test
    @DisplayName("면적을 모르면 금액 중앙값으로 떨어진다")
    void fallsBackToRawMedianWithoutArea() {
        // given
        final List<TradeSample> trades = List.of(
                trade(1_000_000_000L, null, TODAY.minusMonths(1)),
                trade(1_100_000_000L, null, TODAY.minusMonths(2)),
                trade(1_200_000_000L, null, TODAY.minusMonths(3)));

        // when — 매물 면적도 없다
        final CollateralValuation result = CollateralValuator.estimate(
                null, trades, null, null, 1_300_000_000L, RATIO, TODAY);

        // then
        assertThat(result.source()).isEqualTo(CollateralSource.RECENT_TRADE);
        assertThat(result.value()).isEqualTo(1_100_000_000L);
    }

    @Test
    @DisplayName("실거래가 없으면 공시가격을 현실화율로 나눠 쓴다")
    void convertsOfficialPrice() {
        // when — 공시가격 7억 ÷ 0.7 = 10억
        final CollateralValuation result = CollateralValuator.estimate(
                null, List.of(), AREA, 700_000_000L, 1_300_000_000L, RATIO, TODAY);

        // then
        assertThat(result.source()).isEqualTo(CollateralSource.OFFICIAL_PRICE);
        assertThat(result.value()).isEqualTo(1_000_000_000L);
        assertThat(result.isReliable()).isFalse();
    }

    @Test
    @DisplayName("아무것도 없으면 호가를 쓰되 신뢰할 수 없다고 표시한다")
    void fallsBackToAskingPrice() {
        // when
        final CollateralValuation result = CollateralValuator.estimate(
                null, List.of(), AREA, null, 1_500_000_000L, RATIO, TODAY);

        // then — 파는 쪽이 부른 값이라 담보가치보다 높기 쉽다
        assertThat(result.source()).isEqualTo(CollateralSource.ASKING_PRICE);
        assertThat(result.value()).isEqualTo(1_500_000_000L);
        assertThat(result.isReliable()).isFalse();
    }

    private TradeSample trade(long price, BigDecimal areaM2, LocalDate contractDate) {
        return new TradeSample(price, areaM2, contractDate);
    }
}
