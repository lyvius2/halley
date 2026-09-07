package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

/** 비교 우위 추천. 등록된 매물 전체를 견주어 매긴 상대적 우위. */
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
