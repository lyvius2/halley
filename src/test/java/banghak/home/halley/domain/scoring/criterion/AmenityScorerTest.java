package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmenityScorerTest {

    private final AmenityScorer scorer = new AmenityScorer();

    @Test
    @DisplayName("1.3km 내 카테고리별 min(count,3)/3 × 16.67로 집계한다")
    void categoriesContribute() {
        // when / then
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                amenity("CS2", "편의점A")))).score())
                .isEqualByComparingTo("5.56");
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                amenity("CS2", "편의점A"), amenity("CS2", "편의점B"), amenity("CS2", "편의점C"),
                amenity("CS2", "편의점D")))).score())
                .isEqualByComparingTo("16.67");
        assertThat(scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                amenity("CS2", "편의점A"), amenity("MT1", "마트A")))).score())
                .isEqualByComparingTo("11.11");
    }

    @Test
    @DisplayName("1.3km 밖 시설은 집계에서 제외된다")
    void outOfRangeExcluded() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                amenity("CS2", "먼편의점", 2000))));

        // then
        assertThat(result.score()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("반경 내 편의시설이 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("반경 내 편의시설이 없습니다");
    }

    private static NearbyFacility amenity(String subCategory, String name) {
        return amenity(subCategory, name, 500);
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

    private static NearbyFacility amenity(String subCategory, String name, int distanceM) {
        return NearbyFacility.of(1L, "AMENITY", subCategory, name, distanceM, 8, Instant.now());
    }
}
