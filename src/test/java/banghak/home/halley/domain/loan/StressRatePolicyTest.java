package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기준 스트레스 금리 산식 (설계 I116)")
class StressRatePolicyTest {

    private static final BigDecimal FLOOR = new BigDecimal("0.015");
    private static final BigDecimal CAP = new BigDecimal("0.030");

    @Test
    @DisplayName("최고와 현재의 차이를 쓴다 — 하한·상한 사이면 그대로")
    void usesGapBetweenPeakAndCurrent() {
        // given — 최고 5.5%, 현재 3.4% → 차이 2.1%
        final var series = List.of(
                point("202401", "0.034"),
                point("202301", "0.055"),
                point("202206", "0.041"));

        // when — 현재는 '가장 최근 달'이지 '가장 낮은 달'이 아니다
        final var decision = StressRatePolicy.decide(series, FLOOR, CAP).orElseThrow();

        // then
        assertThat(decision.stressRate()).isEqualByComparingTo("0.021");
        assertThat(decision.peakMonth()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(decision.currentMonth()).isEqualTo(YearMonth.of(2024, 1));
    }

    @Test
    @DisplayName("차이가 하한보다 작으면 하한을 쓴다 — 금리가 안정적이어도 스트레스는 걸린다")
    void clampsToFloor() {
        final var series = List.of(point("202401", "0.040"), point("202312", "0.042"));

        final var decision = StressRatePolicy.decide(series, FLOOR, CAP).orElseThrow();

        assertThat(decision.stressRate()).isEqualByComparingTo("0.015");
    }

    @Test
    @DisplayName("차이가 상한보다 크면 상한을 쓴다")
    void clampsToCap() {
        final var series = List.of(point("202401", "0.030"), point("202210", "0.075"));

        final var decision = StressRatePolicy.decide(series, FLOOR, CAP).orElseThrow();

        assertThat(decision.stressRate()).isEqualByComparingTo("0.030");
    }

    @Test
    @DisplayName("자료가 없으면 산출하지 않는다 — 0을 쓰면 스트레스가 사라져 한도가 넉넉해진다")
    void doesNotDecideWithoutData() {
        assertThat(StressRatePolicy.decide(List.of(), FLOOR, CAP)).isEmpty();
        assertThat(StressRatePolicy.decide(null, FLOOR, CAP)).isEmpty();
    }

    @Test
    @DisplayName("산출 근거를 한 줄로 남긴다 — 근거 없는 금리는 검증할 수 없다")
    void explainsItself() {
        final var series = List.of(point("202401", "0.034"), point("202301", "0.055"));

        final var decision = StressRatePolicy.decide(series, FLOOR, CAP).orElseThrow();

        assertThat(decision.source())
                .contains("최고 5.50%")
                .contains("현재 3.40%")
                .contains("2023-01");
    }

    private RatePoint point(String month, String rate) {
        return new RatePoint(
                YearMonth.parse(month, java.time.format.DateTimeFormatter.ofPattern("yyyyMM")),
                new BigDecimal(rate));
    }
}
