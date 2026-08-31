package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.loan.RatePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("금리 국면 지표 (설계 I131)")
class RateCycleIndicatorTest {

    private final RateCycleIndicator indicator = new RateCycleIndicator();

    @Test
    @DisplayName("금리가 내려가면 UP — 매수 여력이 커진다. 부호가 뒤집히는 자리다")
    void fallingRateMeansUp() {
        final var factor = indicator.evaluate(input(rates(
                "0.055", "0.052", "0.049", "0.046", "0.043", "0.040", "0.038"))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("5.50%").contains("3.80%").contains("-1.70%p");
    }

    @Test
    @DisplayName("금리가 올라가면 DOWN")
    void risingRateMeansDown() {
        final var factor = indicator.evaluate(input(rates(
                "0.030", "0.033", "0.036", "0.039", "0.042", "0.045", "0.048"))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("0.25%p 안이면 FLAT — 기준금리 한 번보다 작은 변화를 국면이라 하지 않는다")
    void smallChangeIsFlat() {
        final var factor = indicator.evaluate(input(rates(
                "0.0400", "0.0401", "0.0402", "0.0403", "0.0404", "0.0405", "0.0410"))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("표본이 6개월 미만이면 판단하지 않는다")
    void skipsWhenTooFewPoints() {
        assertThat(indicator.evaluate(input(rates("0.05", "0.04", "0.03")))).isEmpty();
        assertThat(indicator.evaluate(input(List.of()))).isEmpty();
        assertThat(indicator.evaluate(input(null))).isEmpty();
    }

    @Test
    @DisplayName("12개월만 본다 — 그보다 오래된 것은 지금 국면이 아니다")
    void looksAtLastTwelveMonthsOnly() {
        // given — 24개월. 앞 12개월은 급등, 뒤 12개월은 완만한 하락
        final List<RatePoint> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(new RatePoint(YearMonth.of(2024, 1).plusMonths(i), new BigDecimal("0.020")));
        }
        for (int i = 0; i < 12; i++) {
            all.add(new RatePoint(YearMonth.of(2025, 1).plusMonths(i),
                    new BigDecimal("0.060").subtract(new BigDecimal("0.001").multiply(BigDecimal.valueOf(i)))));
        }

        // when
        final var factor = indicator.evaluate(input(all)).orElseThrow();

        // then — 24개월 전체를 봤다면 +2%p 상승이라 DOWN 이 된다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
    }

    @Test
    @DisplayName("순서가 섞여 와도 시간순으로 정렬해 본다")
    void sortsUnorderedInput() {
        final List<RatePoint> shuffled = new ArrayList<>(rates(
                "0.055", "0.052", "0.049", "0.046", "0.043", "0.040", "0.038"));
        Collections.reverse(shuffled);

        assertThat(indicator.evaluate(input(shuffled)).orElseThrow().effect())
                .isEqualTo(ForecastDirection.UP);
    }

    private List<RatePoint> rates(String... values) {
        final List<RatePoint> points = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            points.add(new RatePoint(YearMonth.of(2025, 1).plusMonths(i), new BigDecimal(values[i])));
        }
        return points;
    }

    private ForecastInput input(List<RatePoint> rates) {
        return new ForecastInput(null, List.of(), List.of(), rates, List.of(), null);
    }
}
