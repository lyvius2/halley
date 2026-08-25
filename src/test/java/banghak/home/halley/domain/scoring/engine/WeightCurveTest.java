package banghak.home.halley.domain.scoring.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeightCurveTest {

    @Test
    @DisplayName("등차 0.2: rank 1=3.0, 2=2.8, 6=2.0, 9=1.4, 12=0.8")
    void weightForRank() {
        assertThat(WeightCurve.weightFor(1)).isEqualByComparingTo("3.0");
        assertThat(WeightCurve.weightFor(2)).isEqualByComparingTo("2.8");
        assertThat(WeightCurve.weightFor(6)).isEqualByComparingTo("2.0");
        assertThat(WeightCurve.weightFor(9)).isEqualByComparingTo("1.4");
        assertThat(WeightCurve.weightFor(12)).isEqualByComparingTo("0.8");
    }
}
