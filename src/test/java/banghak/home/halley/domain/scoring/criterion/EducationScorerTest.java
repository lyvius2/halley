package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EducationScorerTest {

    private final EducationScorer scorer = new EducationScorer();

    @Test
    @DisplayName("2km 내 초등·중학·유치원·어린이집 4종이 있으면 100점")
    void allFourTypes() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                school("서울초등학교"), school("서울중학교"),
                childcare("하늘유치원"), childcare("튼튼어린이집"))));

        // then
        assertThat(result.score()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("초등학교만 있으면 25점")
    void onlyElementary() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(),
                TestContexts.context(List.of(school("서울초등학교"))));

        // then
        assertThat(result.score()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("2km 밖 시설은 포함되지 않고, 4종이 모두 없으면 0점")
    void outOfRangeAndNone() {
        // when / then
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                facility("EDUCATION", "SC4", "멀리초등학교", 2500)))).score())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("반경 내 학교가 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("반경 내 학교·보육시설이 없습니다");
    }

    private static NearbyFacility school(String name) {
        return facility("EDUCATION", "SC4", name, 500);
    }

    private static NearbyFacility childcare(String name) {
        return facility("EDUCATION", "PS3", name, 600);
    }

    @Test
    @DisplayName("좌표가 없으면 '데이터 없음'이 아니라 좌표를 채우라는 사유를 남긴다")
    void missingCoordinates() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().noCoordinates().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).contains("좌표");
    }

    private static NearbyFacility facility(String category, String subCategory, String name, int distanceM) {
        return NearbyFacility.of(1L, category, subCategory, name, distanceM, 8, Instant.now());
    }
}
