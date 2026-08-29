package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;

import java.util.ArrayList;
import java.util.List;

public class EducationScorer implements CriterionScorer {

    private static final int WALK_RANGE_M = 2000;

    @Override
    public String code() {
        return "EDUCATION";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> education = ctx.nearbyFacilities().stream()
                .filter(f -> "EDUCATION".equals(f.category()))
                .toList();
        if (education.isEmpty()) {
            if (property.lat() == null || property.lng() == null) {
                return ScoreResult.missingCoordinates();
            }
            return ScoreResult.missing("반경 내 학교·보육시설이 없습니다");
        }
        final List<NearbyFacility> inRange = education.stream()
                .filter(f -> f.distanceM() != null && f.distanceM() <= WALK_RANGE_M)
                .toList();
        int score = 0;
        final List<String> found = new ArrayList<>();
        if (hasName(inRange, "SC4", "초등")) {
            score += 25;
            found.add("초등학교");
        }
        if (hasName(inRange, "SC4", "중학")) {
            score += 25;
            found.add("중학교");
        }
        if (hasName(inRange, "PS3", "유치원")) {
            score += 25;
            found.add("유치원");
        }
        if (hasName(inRange, "PS3", "어린이집")) {
            score += 25;
            found.add("어린이집");
        }
        return ScoreResult.scored(score, String.format("도보 30분 내 %d/4종 확인(%s) · 종류당 25점",
                found.size(), found.isEmpty() ? "없음" : String.join("·", found)));
    }

    private static boolean hasName(List<NearbyFacility> facilities, String subCategory, String keyword) {
        return facilities.stream().anyMatch(f ->
                subCategory.equals(f.subCategory()) && f.name() != null && f.name().contains(keyword));
    }
}
