package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

import java.util.Map;

public class CommuteScorer implements CriterionScorer {

    @Override
    public String code() {
        return "COMMUTE";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final Map<Long, Integer> commuteMinutes = ctx.commuteMinutes();
        if (commuteMinutes == null || commuteMinutes.isEmpty()) {
            if (property.lat() == null || property.lng() == null) {
                return ScoreResult.missingCoordinates();
            }
            return ScoreResult.missing("직장 좌표가 설정된 사용자가 없습니다 — 프로필에서 직장 위치를 지정하세요");
        }
        double sum = 0.0;
        for (final int minutes : commuteMinutes.values()) {
            sum += Math.clamp(100.0 - (minutes - 20) * 1.43, 0.0, 100.0);
        }
        return ScoreResult.scored(sum / commuteMinutes.size());
    }
}
