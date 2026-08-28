package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public class BuildingCountScorer implements CriterionScorer {

    private static final int PEAK_BUILDINGS = 5;

    @Override
    public String code() {
        return "BUILDING_COUNT";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.buildingCount() == null) {
            return ScoreResult.missing("건물동수 없음");
        }
        final int buildings = property.buildingCount();
        if (buildings <= 1) {
            return ScoreResult.scored(0.0);
        }
        if (buildings >= PEAK_BUILDINGS) {
            return ScoreResult.scored(100.0);
        }
        return ScoreResult.scored((buildings - 1) / 4.0 * 100.0);
    }
}
