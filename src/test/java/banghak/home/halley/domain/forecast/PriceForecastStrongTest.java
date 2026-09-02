package banghak.home.halley.domain.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>유력</b>은 언제 붙는가 (설계 I249).
 *
 * <p>AI 가 <b>방향을 말했고</b>(상승·하락), <b>지표를 세어도 같은 쪽</b>이면 유력입니다.
 * 두 갈래가 <b>따로</b> 같은 결론에 닿은 것이라 더 믿을 만합니다.
 *
 * <h4>가장 조심할 자리</h4>
 *
 * <p>AI 가 판단을 보류해 <b>우리가 세어 넣은</b> 경우입니다. 그때 최종 결론과
 * 지표 판정은 <b>같을 수밖에 없습니다</b> — 같은 계산이니까요. 그것을 "일치"로 읽으면
 * <b>판단 보류였던 것이 전부 유력</b>이 됩니다.
 */
@DisplayName("유력 표기 (설계 I249)")
class PriceForecastStrongTest {

    @Test
    @DisplayName("AI 가 상승이라 했고 지표를 세어도 상승이면 유력")
    void bothSaySoUp() {
        assertThat(forecast(ForecastDirection.UP, ForecastDirection.UP, "▲▲▼").strong())
                .isTrue();
    }

    @Test
    @DisplayName("AI 가 하락이라 했고 지표를 세어도 하락이면 유력")
    void bothSaySoDown() {
        assertThat(forecast(ForecastDirection.DOWN, ForecastDirection.DOWN, "▼▼▲").strong())
                .isTrue();
    }

    @Test
    @DisplayName("AI 와 지표가 다른 쪽이면 유력이 아니다")
    void notWhenTheyDisagree() {
        // AI 는 상승이라 했지만 지표를 세면 하락이다
        assertThat(forecast(ForecastDirection.UP, ForecastDirection.UP, "▼▼▲").strong())
                .isFalse();
    }

    /**
     * <b>이 테스트가 이 기능의 급소입니다.</b>
     *
     * <p>AI 가 보류해서 우리가 지표를 세어 상승으로 정한 경우입니다. 최종 결론(UP)과
     * 지표 판정(UP)은 <b>같은 계산의 결과라 반드시 같습니다</b> —
     * 그걸 일치로 읽으면 <b>보류였던 것이 전부 유력</b>이 됩니다.
     */
    @Test
    @DisplayName("AI 가 보류해 우리가 세어 넣은 것은 유력이 아니다")
    void notWhenWeDecidedForTheModel() {
        final PriceForecast forecast =
                forecast(ForecastDirection.UP, ForecastDirection.UNCERTAIN, "▲▲▼");

        assertThat(forecast.outlook().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(forecast.strong())
                .as("견줄 상대가 없었다 — 우리 계산끼리 같은 것을 일치라 부를 수 없다")
                .isFalse();
    }

    @Test
    @DisplayName("AI 가 유지라 해도 유력이 아니다 — 유지는 방향을 말한 것이 아니다")
    void notWhenTheModelSaidFlat() {
        assertThat(forecast(ForecastDirection.FLAT, ForecastDirection.FLAT, "▶▶▶").strong())
                .isFalse();
    }

    /** 옛 전망에는 이 값이 없습니다. <b>모르는 것을 유력이라 하지 않습니다.</b> */
    @Test
    @DisplayName("옛 전망은 유력이 아니다 — AI 가 뭐라 했는지 모른다")
    void notForStaleRows() {
        assertThat(forecast(ForecastDirection.UP, null, "▲▲▼").strong()).isFalse();
    }

    private PriceForecast forecast(ForecastDirection conclusion, ForecastDirection llm, String shape) {
        return new PriceForecast(1L, 1L,
                new PriceOutlook(conclusion, ForecastConfidence.LOW, 12, factors(shape), List.of()),
                llm, ForecastDirection.FLAT, "hash", "claude", Instant.now());
    }

    private List<PriceFactor> factors(String shape) {
        final List<PriceFactor> factors = new java.util.ArrayList<>();
        int i = 0;
        for (final char c : shape.toCharArray()) {
            final ForecastDirection effect = switch (c) {
                case '▲' -> ForecastDirection.UP;
                case '▼' -> ForecastDirection.DOWN;
                default -> ForecastDirection.FLAT;
            };
            factors.add(new PriceFactor("지표" + i++, effect, FactorWeight.MEDIUM, ""));
        }
        return factors;
    }
}
