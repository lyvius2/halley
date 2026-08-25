package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgeScorerTest {

    private final AgeScorer scorer = new AgeScorer();

    @Test
    @DisplayName("신축(연식 0년)은 100점, 연식 10년은 75점이다")
    void recentBuilding() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().approvalYear(2026).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().approvalYear(2016).build(), ctx).score())
                .isEqualByComparingTo("75");
    }

    @Test
    @DisplayName("연식 40년 이상은 10점 하한으로 수렴한다")
    void oldBuildingFloor() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().approvalYear(1986).build(), ctx).score())
                .isEqualByComparingTo("10");
        assertThat(scorer.score(new PropertyBuilder().approvalYear(1970).build(), ctx).score())
                .isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("준공년도가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().approvalYear(null).build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("준공년도 없음");
    }
}
