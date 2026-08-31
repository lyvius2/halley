package banghak.home.halley.domain.forecast;

import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.forecast.indicator.PriceIndicator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("규칙 기반 예측기 (설계 I133)")
class RuleBasedForecasterTest {

    private static final ForecastInput ANY =
            new ForecastInput(null, List.of(), List.of(), List.of(), List.of(), null);

    @Test
    @DisplayName("무게가 큰 요인이 더 많은 표를 가진다 — 실거래 추세가 용도지역보다 무겁다")
    void heavierFactorCarriesMoreVotes() {
        // given — 실거래(HIGH) UP 하나 vs 용도지역(LOW) DOWN 하나
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH),
                factor("용도지역", ForecastDirection.DOWN, FactorWeight.LOW)
        ).forecast(ANY);

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UP);
    }

    @Test
    @DisplayName("표차가 1이면 방향을 주지 않는다 — 근소한 우위를 방향으로 읽지 않는다")
    void narrowMarginIsFlat() {
        // given — HIGH(3) UP vs MEDIUM(2) DOWN → 표차 1
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH),
                factor("전세가율", ForecastDirection.DOWN, FactorWeight.MEDIUM)
        ).forecast(ANY);

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("전부 같은 방향이면 확신도가 높다")
    void unanimousMeansHighConfidence() {
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH),
                factor("전세가율", ForecastDirection.UP, FactorWeight.MEDIUM),
                factor("금리 국면", ForecastDirection.UP, FactorWeight.MEDIUM)
        ).forecast(ANY);

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UP);
        assertThat(outlook.confidence()).isEqualTo(ForecastConfidence.HIGH);
    }

    @Test
    @DisplayName("방향이 갈리면 확신도가 낮다 — 갈리는데 확신이 높을 수는 없다")
    void conflictingFactorsMeanLowConfidence() {
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH),
                factor("실거래 추세2", ForecastDirection.UP, FactorWeight.HIGH),
                factor("전세가율", ForecastDirection.DOWN, FactorWeight.MEDIUM)
        ).forecast(ANY);

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UP);
        assertThat(outlook.confidence()).isEqualTo(ForecastConfidence.LOW);
    }

    @Test
    @DisplayName("FLAT 요인이 많아 지배적이지 않으면 확신도가 낮다")
    void mostlyFlatMeansLowConfidence() {
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH),
                factor("전세가율", ForecastDirection.FLAT, FactorWeight.MEDIUM),
                factor("금리 국면", ForecastDirection.FLAT, FactorWeight.MEDIUM),
                factor("용도지역", ForecastDirection.FLAT, FactorWeight.LOW)
        ).forecast(ANY);

        assertThat(outlook.confidence()).isEqualTo(ForecastConfidence.LOW);
    }

    @Test
    @DisplayName("요인이 하나도 없으면 UNCERTAIN — 억지로 고르지 않는다")
    void noFactorsMeansUncertain() {
        final var outlook = new RuleBasedForecaster(List.of(empty(), empty()), 12).forecast(ANY);

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(outlook.factors()).isEmpty();
        assertThat(outlook.caveats()).isNotEmpty();
    }

    @Test
    @DisplayName("유의사항을 반드시 남긴다 — 비워 두면 모든 것을 봤다고 여긴다")
    void alwaysCarriesCaveats() {
        final var outlook = forecaster(
                factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH)
        ).forecast(ANY);

        assertThat(outlook.caveats()).anyMatch(c -> c.contains("정책 변화"));
    }

    @Test
    @DisplayName("산출 못 한 지표가 있으면 그 사실을 남긴다")
    void reportsMissingIndicators() {
        final var forecaster = new RuleBasedForecaster(List.of(
                indicator(factor("실거래 추세", ForecastDirection.UP, FactorWeight.HIGH)),
                empty(), empty()), 12);

        assertThat(forecaster.forecast(ANY).caveats())
                .anyMatch(c -> c.contains("3개 중 1개만"));
    }

    @Test
    @DisplayName("실거래 추세가 없으면 간접 지표뿐임을 밝힌다")
    void warnsWhenNoHighWeightFactor() {
        final var outlook = forecaster(
                factor("전세가율", ForecastDirection.UP, FactorWeight.MEDIUM),
                factor("금리 국면", ForecastDirection.UP, FactorWeight.MEDIUM)
        ).forecast(ANY);

        assertThat(outlook.caveats()).anyMatch(c -> c.contains("간접 지표만으로"));
    }

    // ── 도우미 ─────────────────────────────────────────────

    private RuleBasedForecaster forecaster(PriceFactor... factors) {
        return new RuleBasedForecaster(
                java.util.Arrays.stream(factors).map(this::indicator).map(i -> (PriceIndicator) i).toList(),
                12);
    }

    private PriceIndicator indicator(PriceFactor factor) {
        return new PriceIndicator() {
            @Override
            public String code() {
                return factor.name();
            }

            @Override
            public Optional<PriceFactor> evaluate(ForecastInput input) {
                return Optional.of(factor);
            }
        };
    }

    private PriceIndicator empty() {
        return new PriceIndicator() {
            @Override
            public String code() {
                return "EMPTY";
            }

            @Override
            public Optional<PriceFactor> evaluate(ForecastInput input) {
                return Optional.empty();
            }
        };
    }

    private PriceFactor factor(String name, ForecastDirection effect, FactorWeight weight) {
        return new PriceFactor(name, effect, weight, "근거");
    }
}
