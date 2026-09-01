package banghak.home.halley.domain.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지표를 세어 방향을 낸다 (설계 I234).
 *
 * <p>한때는 재료가 모자라면 곧바로 "판단 보류"였습니다. 실제로 써 보니
 * <b>거의 모든 매물이 그랬습니다</b> — 지표가 여럿 나와 있는데도요.
 * 알아낸 것을 안 보여 주는 셈이었습니다.
 */
@DisplayName("지표 다수결 (설계 I234)")
class MajorityDirectionTest {

    @Test
    @DisplayName("가장 많은 쪽을 고른다")
    void picksTheMostCommon() {
        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.UP, ForecastDirection.UP, ForecastDirection.DOWN)))
                .isEqualTo(ForecastDirection.UP);

        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.DOWN, ForecastDirection.DOWN, ForecastDirection.FLAT)))
                .isEqualTo(ForecastDirection.DOWN);
    }

    /**
     * <b>오를 수도 있다는 신호를 묻어 두면 기다리다 놓칩니다.</b>
     * 이 도구는 살 집을 고르는 자리입니다.
     */
    @Test
    @DisplayName("동수면 상승이 이긴다")
    void upWinsATie() {
        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.UP, ForecastDirection.DOWN)))
                .isEqualTo(ForecastDirection.UP);

        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.UP, ForecastDirection.FLAT)))
                .isEqualTo(ForecastDirection.UP);

        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.DOWN, ForecastDirection.FLAT, ForecastDirection.UP)))
                .isEqualTo(ForecastDirection.UP);
    }

    /** 하락을 단정하려면 그쪽이 <b>더 많아야</b> 합니다. */
    @Test
    @DisplayName("상승이 없고 유지·하락이 동수면 유지")
    void flatWinsOverDownOnATie() {
        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.FLAT, ForecastDirection.DOWN)))
                .isEqualTo(ForecastDirection.FLAT);

        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.DOWN, ForecastDirection.FLAT,
                ForecastDirection.DOWN, ForecastDirection.FLAT)))
                .isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("셀 것이 없으면 판단 보류 — 없는 방향을 지어내지 않는다")
    void nothingToCountStaysUncertain() {
        assertThat(ForecastDirection.majorityOf(List.of()))
                .isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(ForecastDirection.majorityOf(null))
                .isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.UNCERTAIN, ForecastDirection.UNCERTAIN)))
                .isEqualTo(ForecastDirection.UNCERTAIN);
    }

    /** 요인이 "모르겠다"고 하는 것은 <b>표가 아닙니다.</b> */
    @Test
    @DisplayName("모르겠다는 요인은 표에서 뺀다")
    void uncertainFactorsDoNotVote() {
        assertThat(ForecastDirection.majorityOf(factors(
                ForecastDirection.UNCERTAIN, ForecastDirection.UNCERTAIN,
                ForecastDirection.DOWN)))
                .isEqualTo(ForecastDirection.DOWN);
    }

    /** 화면 문구입니다 — "횡보"는 시세 용어라 한 번 더 생각하게 만듭니다. */
    @Test
    @DisplayName("FLAT 은 '유지'로 적는다")
    void flatReadsAsMaintained() {
        assertThat(ForecastDirection.FLAT.label()).isEqualTo("유지");
        assertThat(ForecastDirection.UP.label()).isEqualTo("상승");
        assertThat(ForecastDirection.DOWN.label()).isEqualTo("하락");
    }

    private List<PriceFactor> factors(ForecastDirection... effects) {
        return java.util.Arrays.stream(effects)
                .map(e -> new PriceFactor("지표", e, FactorWeight.MEDIUM, "근거"))
                .toList();
    }
}
