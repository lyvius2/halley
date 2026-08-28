package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.geo.GreenCategory;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;

import java.util.List;
import java.util.OptionalInt;

/**
 * 녹색환경 — 공원·산·하천 3종을 각각 <b>최근접 시설까지의 도보시간</b>으로 채점한다(설계 5.2 · I42).
 *
 * <pre>
 * 종류별 점수 = 33.3 × clamp((20 − t) / 15, 0, 1)     t = 최근접 도보 분
 * </pre>
 *
 * 존재 여부만 보던 이전 규칙은 서울 후보끼리 전부 100점 동점이 되어 순위에 기여하지 못했다.
 * 5분 이내 만점·20분 이상 0점은 `STATION`과 같은 형태로, 두 항목의 산식을 일관되게 유지한다.
 */
public class GreenScorer implements CriterionScorer {

    private static final double MAX_PER_KIND = 100.0 / 3;
    private static final int FULL_SCORE_MINUTES = 5;
    private static final int ZERO_SCORE_MINUTES = 20;

    @Override
    public String code() {
        return "GREEN";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final List<NearbyFacility> green = ctx.nearbyFacilities().stream()
                .filter(f -> "GREEN".equals(f.category()))
                .toList();
        if (green.isEmpty()) {
            if (property.lat() == null || property.lng() == null) {
                return ScoreResult.missingCoordinates();
            }
            return ScoreResult.missing("반경 내 공원·산·하천이 없습니다");
        }
        double score = 0.0;
        for (final GreenCategory category : GreenCategory.values()) {
            final OptionalInt nearest = nearestWalkMinutes(green, category);
            if (nearest.isPresent()) {
                score += scoreByWalkMinutes(nearest.getAsInt());
            }
        }
        return ScoreResult.scored(score);
    }

    /**
     * 판정은 `sub_category`(PoiDataService가 카카오 `category_name`으로 분류해 저장)로 한다.
     * 장소명 매칭은 "떡산 롯데백화점"을 산으로 잡는 오탐이 많아 쓰지 않는다.
     */
    private static OptionalInt nearestWalkMinutes(List<NearbyFacility> facilities, GreenCategory category) {
        return facilities.stream()
                .filter(f -> category.name().equals(f.subCategory()))
                .filter(f -> f.walkMinutes() != null)
                .mapToInt(NearbyFacility::walkMinutes)
                .min();
    }

    private static double scoreByWalkMinutes(int walkMinutes) {
        if (walkMinutes <= FULL_SCORE_MINUTES) {
            return MAX_PER_KIND;
        }
        if (walkMinutes >= ZERO_SCORE_MINUTES) {
            return 0.0;
        }
        return MAX_PER_KIND * (ZERO_SCORE_MINUTES - walkMinutes) / (ZERO_SCORE_MINUTES - FULL_SCORE_MINUTES);
    }
}
