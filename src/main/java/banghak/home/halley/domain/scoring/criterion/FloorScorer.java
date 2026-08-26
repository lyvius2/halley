package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

public class FloorScorer implements CriterionScorer {

    private static final int FLOOR_PEAK = 7;

    @Override
    public String code() {
        return "FLOOR";
    }

    @Override
    public ScoringType type() {
        return ScoringType.AUTO;
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.floorBand() != null) {
            return ScoreResult.scored(property.floorBand() == FloorBand.LOW ? 0.0 : 100.0);
        }
        if (property.floorNo() != null) {
            return ScoreResult.scored(scoreFloor(property.floorNo()));
        }
        return ScoreResult.missing("층 정보 없음");
    }

    private double scoreFloor(int floor) {
        if (floor >= FLOOR_PEAK) {
            return 100.0;
        }
        if (floor <= 1) {
            return 0.0;
        }
        return (floor - 1) / 5.0 * 100.0;
    }
}
