package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

import java.util.List;

public class GreenScorer implements CriterionScorer {

    private static final int WALK_RANGE_M = 2000;

    @Override
    public String code() {
        return "GREEN";
    }

    @Override
    public ScoringType type() {
        return ScoringType.HYBRID;
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> green = ctx.nearbyFacilities().stream()
                .filter(f -> "GREEN".equals(f.category()))
                .toList();
        if (green.isEmpty()) {
            return ScoreResult.missing("녹색환경 데이터 없음");
        }
        final List<NearbyFacility> inRange = green.stream()
                .filter(f -> f.distanceM() != null && f.distanceM() <= WALK_RANGE_M)
                .toList();
        double score = 0.0;
        if (containsName(inRange, "공원")) {
            score += 100.0 / 3;
        }
        if (containsName(inRange, "산")) {
            score += 100.0 / 3;
        }
        if (containsName(inRange, "천") || containsName(inRange, "강")) {
            score += 100.0 / 3;
        }
        return ScoreResult.scored(score);
    }

    private static boolean containsName(List<NearbyFacility> facilities, String keyword) {
        return facilities.stream().anyMatch(f -> f.name() != null && f.name().contains(keyword));
    }
}
