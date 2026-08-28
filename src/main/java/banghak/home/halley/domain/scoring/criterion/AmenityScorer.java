package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;

import java.util.List;

public class AmenityScorer implements CriterionScorer {

    private static final int WALK_RANGE_M = 1300;
    private static final List<String> CATEGORIES = List.of("CS2", "MT1", "FD6", "CE7", "CT1", "BK9");
    private static final double POINTS_PER_CATEGORY = 100.0 / 6;

    @Override
    public String code() {
        return "AMENITY";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> amenity = ctx.nearbyFacilities().stream()
                .filter(f -> "AMENITY".equals(f.category()))
                .toList();
        if (amenity.isEmpty()) {
            if (property.lat() == null || property.lng() == null) {
                return ScoreResult.missingCoordinates();
            }
            return ScoreResult.missing("반경 내 편의시설이 없습니다");
        }
        final List<NearbyFacility> inRange = amenity.stream()
                .filter(f -> f.distanceM() != null && f.distanceM() <= WALK_RANGE_M)
                .toList();
        double score = 0.0;
        for (final String group : CATEGORIES) {
            final long count = inRange.stream().filter(f -> group.equals(f.subCategory())).count();
            score += Math.min(count, 3) / 3.0 * POINTS_PER_CATEGORY;
        }
        return ScoreResult.scored(score);
    }
}
