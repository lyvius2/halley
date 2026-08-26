package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.ScoringType;

import java.util.List;

public class EducationScorer implements CriterionScorer {

    private static final int WALK_RANGE_M = 2000;

    @Override
    public String code() {
        return "EDUCATION";
    }

    @Override
    public ScoringType type() {
        return ScoringType.AUTO;
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> education = ctx.nearbyFacilities().stream()
                .filter(f -> "EDUCATION".equals(f.category()))
                .toList();
        if (education.isEmpty()) {
            return ScoreResult.missing("교육여건 데이터 없음");
        }
        final List<NearbyFacility> inRange = education.stream()
                .filter(f -> f.distanceM() != null && f.distanceM() <= WALK_RANGE_M)
                .toList();
        int score = 0;
        if (hasName(inRange, "SC4", "초등")) {
            score += 25;
        }
        if (hasName(inRange, "SC4", "중학")) {
            score += 25;
        }
        if (hasName(inRange, "PS3", "유치원")) {
            score += 25;
        }
        if (hasName(inRange, "PS3", "어린이집")) {
            score += 25;
        }
        return ScoreResult.scored(score);
    }

    private static boolean hasName(List<NearbyFacility> facilities, String subCategory, String keyword) {
        return facilities.stream().anyMatch(f ->
                subCategory.equals(f.subCategory()) && f.name() != null && f.name().contains(keyword));
    }
}
