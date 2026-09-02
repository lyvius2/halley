package banghak.home.halley.domain.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지표를 세어 방향을 정한다 (설계 I248).
 *
 * <p>표기는 <b>{@code ▲}=상승 · {@code ▶}=유지 · {@code ▼}=하락</b> 으로 씁니다.
 * 숫자 셋을 나열하는 것보다 <b>화면에 보이는 모양 그대로</b> 읽는 편이
 * 규칙과 견주기 쉽습니다.
 */
@DisplayName("지표 판정 (설계 I248)")
class FactorTallyTest {

    @Nested
    @DisplayName("규칙")
    class Rules {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                // ① 셀 것이 없다
                "'',       FLAT",
                "▶,        FLAT",
                "▶▶▶▶,    FLAT",
                // ② 과반수가 유지면 무조건 유지
                "▶▶▶▼,    FLAT",
                "▶▶▼,     FLAT",
                "▶▶▶▲▲,   FLAT",
                // ③ 유지 == 하락, 상승이 그보다 적으면 유지 (유지 > 하락)
                "▶▼,       FLAT",
                "▶▶▼▼,    FLAT",
                "▲▶▶▼▼,   FLAT",
                // ④ 상승 >= 하락이면 상승 (동수면 상승)
                "▲▼▼▲,    UP",
                "▲▶▶▼,    UP",
                "▲▲▶,     UP",
                "▲,        UP",
                "▲▼,       UP",
                // ⑤ 하락이 더 많으면 하락
                "▶▶▼▼▼,   DOWN",
                "▲▼▼,     DOWN",
                "▼,        DOWN",
                "▲▶▼▼,    DOWN"
        })
        @DisplayName("지표 구성이 방향을 정한다")
        void decidesFromFactors(String shape, ForecastDirection expected) {
            assertThat(FactorTally.of(factors(shape)).direction()).isEqualTo(expected);
        }
    }

    /**
     * 스크린샷으로 확인한 실제 사례 (설계 I248).
     *
     * <p>장기추세 ▲ · 금리 ▼ · 전세가율 ▼ · 정비기대 ▲ 인데 <b>판단 보류</b>로
     * 떠 있었습니다. 무게로 세면 하락(4:3)이지만 <b>머릿수로는 2:2 동수</b>이고,
     * 동수면 상승입니다.
     */
    @Test
    @DisplayName("무게가 아니라 머릿수로 센다 — 낮음 하나가 보통 하나를 못 이긴다")
    void countsHeadsNotWeights() {
        final List<PriceFactor> factors = List.of(
                new PriceFactor("장기 가격 추세", ForecastDirection.UP, FactorWeight.MEDIUM, ""),
                new PriceFactor("금리 국면", ForecastDirection.DOWN, FactorWeight.MEDIUM, ""),
                new PriceFactor("낮은 전세가율", ForecastDirection.DOWN, FactorWeight.MEDIUM, ""),
                new PriceFactor("노후도·대단지 정비 기대", ForecastDirection.UP, FactorWeight.LOW, ""));

        assertThat(FactorTally.of(factors).direction())
                .as("무게로 세면 하락(4:3)이 된다 — 그건 원하는 바가 아니다")
                .isEqualTo(ForecastDirection.UP);
    }

    @Nested
    @DisplayName("신호 없음")
    class NoSignal {

        @Test
        @DisplayName("지표가 하나도 없으면 신호 없음")
        void nothingAtAll() {
            assertThat(FactorTally.of(List.of()).noSignal()).isTrue();
            assertThat(FactorTally.of(null).noSignal()).isTrue();
        }

        @Test
        @DisplayName("전부 유지여도 신호 없음")
        void allFlat() {
            assertThat(FactorTally.of(factors("▶▶▶")).noSignal()).isTrue();
        }

        /**
         * 하나라도 방향을 주면 <b>신호는 있는 것</b>입니다 — 결론이 유지여도요.
         * 🤔 는 "볼 재료가 없었다"는 표시지 "판단이 유지"라는 표시가 아닙니다.
         */
        @Test
        @DisplayName("하나라도 방향을 주면 신호가 있다 — 결론이 유지여도")
        void oneDirectionIsStillASignal() {
            final FactorTally tally = FactorTally.of(factors("▶▶▶▼"));

            assertThat(tally.direction()).isEqualTo(ForecastDirection.FLAT);
            assertThat(tally.noSignal()).as("유지 결론과 신호 없음은 다르다").isFalse();
        }
    }

    /** {@code null} 방향도 유지로 센다 — 방향을 안 준 것은 다 같다 */
    @Test
    @DisplayName("방향이 비어 있어도 유지로 센다")
    void treatsMissingDirectionAsFlat() {
        final List<PriceFactor> factors = new ArrayList<>();
        factors.add(new PriceFactor("이름", null, FactorWeight.MEDIUM, ""));
        factors.add(new PriceFactor("이름2", ForecastDirection.UNCERTAIN, FactorWeight.MEDIUM, ""));

        final FactorTally tally = FactorTally.of(factors);

        assertThat(tally.flat()).isEqualTo(2);
        assertThat(tally.noSignal()).isTrue();
    }

    private List<PriceFactor> factors(String shape) {
        final List<PriceFactor> factors = new ArrayList<>();
        if (shape == null) {
            return factors;
        }
        int i = 0;
        for (final char c : shape.toCharArray()) {
            final ForecastDirection effect = switch (c) {
                case '▲' -> ForecastDirection.UP;
                case '▼' -> ForecastDirection.DOWN;
                case '▶' -> ForecastDirection.FLAT;
                default -> throw new IllegalArgumentException("모르는 표기: " + c);
            };
            factors.add(new PriceFactor("지표" + i++, effect, FactorWeight.MEDIUM, ""));
        }
        return factors;
    }
}
