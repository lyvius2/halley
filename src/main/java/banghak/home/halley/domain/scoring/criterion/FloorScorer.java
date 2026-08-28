package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.Property;

public class FloorScorer implements CriterionScorer {

    private static final int FLOOR_PEAK = 7;

    @Override
    public String code() {
        return "FLOOR";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.floorBand() != null) {
            final boolean low = property.floorBand() == FloorBand.LOW;
            return ScoreResult.scored(low ? 0.0 : 100.0,
                    low ? "저층 표기 → 0점" : "중·고층 표기 → 동점 만점");
        }
        if (property.floorNo() != null) {
            final int floor = property.floorNo();
            return ScoreResult.scored(scoreFloor(floor), floor >= FLOOR_PEAK
                    ? String.format("%d층 · %d층 이상은 모두 만점", floor, FLOOR_PEAK)
                    : String.format("%d층 · 1층 0점 ~ 6층 100점 선형", floor));
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
