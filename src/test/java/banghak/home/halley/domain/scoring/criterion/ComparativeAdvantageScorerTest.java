package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("비교 우위 추천 채점 (설계 I61)")
class ComparativeAdvantageScorerTest {

    private final ComparativeAdvantageScorer scorer = new ComparativeAdvantageScorer();

    @Test
    @DisplayName("저장된 비교 점수를 쓰고 근거에 '몇 개 중 몇 위'를 앞세운다")
    void usesStoredComparativeScore() {
        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().build(),
                TestContexts.contextWithComparative(
                        new BigDecimal("91.00"), "다른 매물보다 역이 가깝습니다", 1, 5));

        // then
        assertThat(result.isComputed()).isTrue();
        assertThat(result.score()).isEqualByComparingTo("91.00");
        assertThat(result.explanation()).isEqualTo("5개 중 1위 · 다른 매물보다 역이 가깝습니다");
    }

    @Test
    @DisplayName("분석 전이면 미산출로 남고 최소 매물 수를 안내한다")
    void missingBeforeAnalysis() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).contains("4개 이상");
    }

    @Test
    @DisplayName("순위 정보가 없으면 이유만 근거로 남긴다")
    void keepsReasonWithoutRank() {
        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().build(),
                TestContexts.contextWithComparative(new BigDecimal("55.00"), "무난합니다", null, null));

        // then
        assertThat(result.explanation()).isEqualTo("무난합니다");
    }
}
