package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;

import java.util.List;

public class StationScorer implements CriterionScorer {

    @Override
    public String code() {
        return "STATION";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> stations = ctx.nearbyFacilities().stream()
                .filter(f -> "STATION".equals(f.category()))
                .filter(f -> f.walkMinutes() != null)
                .toList();
        if (stations.isEmpty()) {
            if (property.lat() == null || property.lng() == null) {
                return ScoreResult.missingCoordinates();
            }
            return ScoreResult.missing("반경 내 지하철역이 없습니다");
        }
        final int nearest = stations.stream()
                .mapToInt(NearbyFacility::walkMinutes)
                .min().orElse(Integer.MAX_VALUE);
        if (nearest <= 5) {
            return ScoreResult.scored(100.0);
        }
        if (nearest > 20) {
            return ScoreResult.scored(0.0);
        }
        return ScoreResult.scored(100.0 * (20 - nearest) / 15.0);
    }
}
