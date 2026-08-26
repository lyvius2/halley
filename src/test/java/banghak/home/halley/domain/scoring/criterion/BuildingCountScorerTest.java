package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildingCountScorerTest {

    private final BuildingCountScorer scorer = new BuildingCountScorer();

    @Test
    @DisplayName("1동은 0점, 2~4동은 선형 증가, 5동 이상은 전부 100점이다")
    void buildingCountBoundaries() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().buildingCount(1).build(), ctx).score())
                .isEqualByComparingTo("0");
        assertThat(scorer.score(new PropertyBuilder().buildingCount(2).build(), ctx).score())
                .isEqualByComparingTo("25");
        assertThat(scorer.score(new PropertyBuilder().buildingCount(3).build(), ctx).score())
                .isEqualByComparingTo("50");
        assertThat(scorer.score(new PropertyBuilder().buildingCount(4).build(), ctx).score())
                .isEqualByComparingTo("75");
        assertThat(scorer.score(new PropertyBuilder().buildingCount(5).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().buildingCount(8).build(), ctx).score())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("건물동수가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().buildingCount(null).build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("건물동수 없음");
    }
}
