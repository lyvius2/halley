package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

/**
 * 세대수 — 적을수록 낮은 점수, <b>350세대 이상은 모두 만점</b> (설계 5.2.2 · I49).
 *
 * <p>나홀로·소규모 단지는 재건축 동의율 확보가 어렵고 환금성이 낮다는 판단을 반영한다.
 * 값은 네이버 붙여넣기로 확보되는 `total_households`를 그대로 쓴다(수기 입력이던 건물동 수를 대체).
 */
public class HouseholdsScorer implements CriterionScorer {

    private static final int PEAK_HOUSEHOLDS = 350;

    @Override
    public String code() {
        return "HOUSEHOLDS";
    }

    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        if (property.totalHouseholds() == null) {
            return ScoreResult.missing("세대수 없음 — 매물 수정에서 총 세대수를 입력하세요");
        }
        final int households = property.totalHouseholds();
        if (households <= 0) {
            return ScoreResult.missing("세대수 없음 — 매물 수정에서 총 세대수를 입력하세요");
        }
        if (households >= PEAK_HOUSEHOLDS) {
            return ScoreResult.scored(100.0,
                    String.format("%,d세대 · %d세대 이상은 모두 만점", households, PEAK_HOUSEHOLDS));
        }
        return ScoreResult.scored(households * 100.0 / PEAK_HOUSEHOLDS,
                String.format("%,d세대 · %d세대를 100점으로 본 비례 점수", households, PEAK_HOUSEHOLDS));
    }
}
