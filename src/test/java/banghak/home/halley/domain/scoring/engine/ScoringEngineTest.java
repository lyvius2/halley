package banghak.home.halley.domain.scoring.engine;

import banghak.home.halley.domain.scoring.criterion.HouseholdsScorer;
import banghak.home.halley.domain.scoring.criterion.CriterionScorer;
import banghak.home.halley.domain.scoring.criterion.FloorScorer;
import banghak.home.halley.domain.scoring.criterion.ParkingScorer;
import banghak.home.halley.domain.scoring.criterion.ScoringContext;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    @Test
    @DisplayName("총점은 effective_score 가중평균으로 계산한다")
    void weightedAverage() {
        // given
        final List<CriterionScorer> scorers = List.of(new FloorScorer(), new ParkingScorer());
        final Map<String, BigDecimal> weights = Map.of("FLOOR", new BigDecimal("2.0"), "PARKING", new BigDecimal("1.0"));

        // when
        final PropertyScoringResult result = engine.score(
                new PropertyBuilder().floorNo(6).build(), TestContexts.context(), scorers, weights, Map.of());

        // then
        assertThat(result.totalScore()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("수동 점수가 있으면 자동 점수보다 우선한다")
    void manualOverridesAuto() {
        // given
        final List<CriterionScorer> scorers = List.of(new FloorScorer(), new ParkingScorer());
        final Map<String, BigDecimal> weights = Map.of("FLOOR", new BigDecimal("2.0"), "PARKING", new BigDecimal("1.0"));
        final Map<String, BigDecimal> manual = Map.of("FLOOR", new BigDecimal("50.0"));

        // when
        final PropertyScoringResult result = engine.score(
                new PropertyBuilder().floorNo(6).build(), TestContexts.context(), scorers, weights, manual);

        // then
        assertThat(result.totalScore()).isEqualByComparingTo("66.67");
        assertThat(result.criteria().stream()
                .filter(c -> c.code().equals("FLOOR")).findFirst().orElseThrow().effectiveScore())
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("점수가 없는 기준(MISSING)은 총점 계산에서 제외된다")
    void missingCriterionExcluded() {
        // given
        final List<CriterionScorer> scorers = List.of(new FloorScorer(), new ParkingScorer(), new HouseholdsScorer());
        final Map<String, BigDecimal> weights = Map.of(
                "FLOOR", new BigDecimal("2.0"),
                "PARKING", new BigDecimal("1.0"),
                "HOUSEHOLDS", new BigDecimal("2.0"));

        // when
        final PropertyScoringResult result = engine.score(
                new PropertyBuilder().floorNo(6).buildingCount(null).build(),
                TestContexts.context(), scorers, weights, Map.of());

        // then
        assertThat(result.totalScore()).isEqualByComparingTo("100");
        assertThat(result.criteria().stream()
                .filter(c -> c.code().equals("HOUSEHOLDS")).findFirst().orElseThrow().effectiveScore())
                .isNull();
    }
}
