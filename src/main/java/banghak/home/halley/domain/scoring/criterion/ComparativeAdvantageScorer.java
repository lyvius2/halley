package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

/**
 * 비교 우위 추천 — 등록된 매물 전체를 견주어 매긴 상대적 우위 (설계 I61).
 *
 * <p>AI 추천도(I59)와 달리 <b>다른 매물과의 비교</b>에서 나온 점수입니다. LLM 호출은 매물 전체를
 * 한 번에 던지는 무거운 작업이라 채점 루프에서 돌리지 않고, `ComparativeAnalysisService`가
 * 저장해 둔 값을 `ScoringContext`로 받아 그대로 씁니다.
 */
public class ComparativeAdvantageScorer implements CriterionScorer {

    @Override
    public String code() {
        return "COMPARATIVE_ADVANTAGE";
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (ctx.comparativeScore() == null) {
            return ScoreResult.missing("비교 우위 분석 없음 — 매물이 4개 이상일 때 분석을 실행하세요");
        }
        final String rank = ctx.comparativeRank() == null || ctx.comparativeCount() == null
                ? ""
                : ctx.comparativeCount() + "개 중 " + ctx.comparativeRank() + "위 · ";
        final String reason = ctx.comparativeReason() == null || ctx.comparativeReason().isBlank()
                ? "비교 분석 결과" : ctx.comparativeReason();
        return ScoreResult.scored(ctx.comparativeScore().doubleValue(), rank + reason);
    }
}
