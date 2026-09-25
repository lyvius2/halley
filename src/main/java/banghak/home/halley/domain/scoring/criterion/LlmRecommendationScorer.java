package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

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
