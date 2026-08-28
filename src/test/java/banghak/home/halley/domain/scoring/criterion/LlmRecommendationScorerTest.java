package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI 추천도 채점 (설계 I59)")
class LlmRecommendationScorerTest {

    private final LlmRecommendationScorer scorer = new LlmRecommendationScorer();

    @Test
    @DisplayName("저장된 추천도를 그대로 점수로 쓰고 이유를 근거로 남긴다")
    void usesStoredRecommendation() {
        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().build(),
                TestContexts.contextWithLlm(new BigDecimal("82.00"), "직장까지 가깝습니다"));

        // then
        assertThat(result.isComputed()).isTrue();
        assertThat(result.score()).isEqualByComparingTo("82.00");
        assertThat(result.explanation()).isEqualTo("직장까지 가깝습니다");
    }

    @Test
    @DisplayName("아직 산출되지 않았으면 미산출로 남는다 — LLM 호출은 채점 루프에서 하지 않는다")
    void missingWhenNotComputedYet() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).contains("AI 추천도 없음");
    }

    @Test
    @DisplayName("이유가 비어 있어도 점수는 살린다")
    void keepsScoreWhenReasonBlank() {
        // when
        final ScoreResult result = scorer.score(
                new PropertyBuilder().build(),
                TestContexts.contextWithLlm(new BigDecimal("55.00"), "  "));

        // then
        assertThat(result.isComputed()).isTrue();
        assertThat(result.explanation()).isEqualTo("AI 판단");
    }
}
