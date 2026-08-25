package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComfortScorerTest {

    private final ComfortScorer scorer = new ComfortScorer();

    @Test
    @DisplayName("사용자 1~5 점수의 평균에 20을 곱한다")
    void averagesUserScores() {
        // given
        final PropertyBuilder property = new PropertyBuilder();

        // when / then
        assertThat(scorer.score(property.build(), TestContexts.context(0L, List.of(5))).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(property.build(), TestContexts.context(0L, List.of(1))).score())
                .isEqualByComparingTo("20");
        assertThat(scorer.score(property.build(), TestContexts.context(0L, List.of(3, 5))).score())
                .isEqualByComparingTo("80");
        assertThat(scorer.score(property.build(), TestContexts.context(0L, List.of(1, 2, 3, 4, 5))).score())
                .isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("사용자 평가가 없으면 MISSING으로 기록된다")
    void missing() {
        // given
        final ScoringContext ctx = TestContexts.context();

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), ctx);

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("사용자 평가 없음");
    }
}
