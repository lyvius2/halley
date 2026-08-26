package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingScorerTest {

    private final ParkingScorer scorer = new ParkingScorer();

    @Test
    @DisplayName("세대당 1대는 100점, 0.5대는 50점, 1대 초과는 100점으로 클램프된다")
    void parkingRatios() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().parkingPerHousehold(new BigDecimal("1.0")).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().parkingPerHousehold(new BigDecimal("0.5")).build(), ctx).score())
                .isEqualByComparingTo("50");
        assertThat(scorer.score(new PropertyBuilder().parkingPerHousehold(new BigDecimal("1.2")).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().parkingPerHousehold(new BigDecimal("0")).build(), ctx).score())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("주차 정보가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().parkingPerHousehold(null).build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("주차 정보 없음");
    }
}
