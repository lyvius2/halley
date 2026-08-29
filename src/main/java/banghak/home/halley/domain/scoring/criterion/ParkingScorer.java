package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public class ParkingScorer implements CriterionScorer {

    @Override
    public String code() {
        return "PARKING";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.parkingPerHousehold() == null) {
            return ScoreResult.missing("주차 정보 없음");
        }
        final double targetValue = property.parkingPerHousehold().doubleValue() * 100.0;
        return ScoreResult.scored(Math.clamp(targetValue, 0.0, 100.0),
                String.format("세대당 주차 %s대 → 1.0대를 100점으로 환산", property.parkingPerHousehold()));
    }
}
