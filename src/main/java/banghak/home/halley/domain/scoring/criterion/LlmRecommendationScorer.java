package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

/**
 * AI 추천도 — LLM이 매물 정보와 구매자들의 직장 위치를 보고 매긴 0~100점 (설계 I59).
 *
 * <p>다른 항목과 달리 <b>점수를 여기서 계산하지 않습니다</b>. LLM 호출은 느리고 돈이 들어
 * 채점 루프 안에서 돌릴 수 없으므로, `LlmRecommendationService`가 미리 저장해 둔 값을
 * `ScoringContext`로 받아 그대로 씁니다. 값이 없으면 미산출로 남습니다.
 */
public class LlmRecommendationScorer implements CriterionScorer {

    @Override
    public String code() {
        return "LLM_RECOMMENDATION";
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (ctx.llmScore() == null) {
            return ScoreResult.missing("AI 추천도 없음 — LLM 연동이 꺼져 있거나 아직 산출되지 않았습니다");
        }
        final String reason = ctx.llmReason() == null || ctx.llmReason().isBlank()
                ? "AI 판단" : ctx.llmReason();
        return ScoreResult.scored(ctx.llmScore().doubleValue(), reason);
    }
}
