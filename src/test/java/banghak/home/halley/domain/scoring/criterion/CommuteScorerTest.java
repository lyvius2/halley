package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommuteScorerTest {

    private final CommuteScorer scorer = new CommuteScorer();

    @Test
    @DisplayName("20분 이하 만점, 초과 시 1.43점/분 감점, 90분 이상 0점으로 클램프")
    void commuteTimeBoundaries() {
        // given / when / then
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(Map.of(1L, 20))).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(Map.of(1L, 35))).score())
                .isEqualByComparingTo("78.55");
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(Map.of(1L, 90))).score())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("전 사용자 점수의 평균을 사용한다")
    void averagesUsers() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(),
                TestContexts.context(Map.of(1L, 20, 2L, 60)));

        // then
        assertThat(result.score()).isEqualByComparingTo("71.4");
    }

    @Test
    @DisplayName("통근 데이터가 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("통근 데이터 없음");
    }
}
