package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.geo.GreenCategory;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreenScorerTest {

    private final GreenScorer scorer = new GreenScorer();

    @Test
    @DisplayName("3종 모두 도보 5분 이내면 100점")
    void allThreeWithinFullScoreRange() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                green(GreenCategory.PARK, "서울숲공원", 3),
                green(GreenCategory.MOUNTAIN, "북한산", 5),
                green(GreenCategory.RIVER, "청계천", 1))));

        // then
        assertThat(result.score()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("도보시간이 멀수록 종류별 점수가 선형으로 줄어든다 — 광화문 실측 조합")
    void scoresByWalkMinutes() {
        // given — 경복공원 190m(4분) · 중학천 531m(10분) · 녹산 802m(16분)
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                green(GreenCategory.PARK, "경복공원", 4),
                green(GreenCategory.RIVER, "중학천", 10),
                green(GreenCategory.MOUNTAIN, "녹산", 16))));

        // then — 33.33 + 22.22 + 8.89
        assertThat(result.score()).isEqualByComparingTo("64.44");
    }

    @Test
    @DisplayName("도보 20분 이상인 종류는 0점으로 처리한다")
    void beyondTwentyMinutesScoresZero() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                green(GreenCategory.PARK, "가까운공원", 5),
                green(GreenCategory.MOUNTAIN, "먼산", 20),
                green(GreenCategory.RIVER, "먼하천", 25))));

        // then — 공원 33.33만 남는다
        assertThat(result.score()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("같은 종류가 여러 건이면 가장 가까운 것으로 채점한다")
    void usesNearestOfSameKind() {
        // when — 하천은 구간마다 별도 POI로 잡힌다(중랑천 3건 등)
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                green(GreenCategory.RIVER, "중랑천", 16),
                green(GreenCategory.RIVER, "중랑천", 5),
                green(GreenCategory.RIVER, "당현천", 26))));

        // then — 5분짜리 기준으로 만점
        assertThat(result.score()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("분류되지 않은 GREEN 시설만 있으면 0점이 된다")
    void unclassifiedGivesZero() {
        // given — 예전처럼 sub_category에 그룹코드만 들어 있는 데이터
        final NearbyFacility legacy = NearbyFacility.of(1L, "GREEN", "AT4", "노원문화의거리", 672, 13, Instant.now());

        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(legacy)));

        // then
        assertThat(result.score()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("반경 내 녹지가 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("반경 내 공원·산·하천이 없습니다");
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

    private static NearbyFacility green(GreenCategory category, String name, int walkMinutes) {
        final int distanceM = (int) Math.round(walkMinutes * 67 / 1.3);
        return NearbyFacility.of(1L, "GREEN", category.name(), name, distanceM, walkMinutes, Instant.now());
    }
}
