package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloorScorerTest {

    private final FloorScorer scorer = new FloorScorer();

    @Test
    @DisplayName("숫자 층수: 1층은 0점, 6층은 100점, 7층 이상은 전부 100점이다")
    void numericFloorBoundaries() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().floorNo(1).build(), ctx).score())
                .isEqualByComparingTo("0");
        assertThat(scorer.score(new PropertyBuilder().floorNo(2).build(), ctx).score())
                .isEqualByComparingTo("20");
        assertThat(scorer.score(new PropertyBuilder().floorNo(6).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().floorNo(7).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().floorNo(20).build(), ctx).score())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("밴드 표기: 저=0점, 중/고=100점 동점이다")
    void bandFloorScores() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when / then
        assertThat(scorer.score(new PropertyBuilder().floorBand(FloorBand.LOW).build(), ctx).score())
                .isEqualByComparingTo("0");
        assertThat(scorer.score(new PropertyBuilder().floorBand(FloorBand.MID).build(), ctx).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().floorBand(FloorBand.HIGH).build(), ctx).score())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("밴드 표기가 숫자 표기보다 우선한다")
    void bandPrecedesNumber() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().floorNo(1).floorBand(FloorBand.HIGH).build(), ctx);

        // then
        assertThat(result.score()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("층 정보가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().floorNo(null).floorBand(null).build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("층 정보 없음");
    }
}
