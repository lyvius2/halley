package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.scoring.support.PropertyBuilder;
import banghak.home.halley.domain.scoring.support.TestContexts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationScorerTest {

    private final StationScorer scorer = new StationScorer();

    @Test
    @DisplayName("최근접역 도보 5분 이하 100점, 5~20분은 선형 감점, 20분 초과 0점")
    void stationWalkTimeBoundaries() {
        // given / when / then
        assertThat(scorer.score(new PropertyBuilder().build(), ctx(station(5))).score())
                .isEqualByComparingTo("100");
        assertThat(scorer.score(new PropertyBuilder().build(), ctx(station(10))).score())
                .isEqualByComparingTo("66.67");
        assertThat(scorer.score(new PropertyBuilder().build(), ctx(station(20))).score())
                .isEqualByComparingTo("0");
        assertThat(scorer.score(new PropertyBuilder().build(), ctx(station(30))).score())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("여러 역 중 가장 가까운 역으로 계산한다")
    void nearestStationUsed() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(),
                TestContexts.context(List.of(station(10), station(3))));

        // then
        assertThat(result.score()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("역 데이터가 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("역세권 데이터 없음");
    }

    private static ScoringContext ctx(NearbyFacility station) {
        return TestContexts.context(List.of(station));
    }

    private static NearbyFacility station(int walkMinutes) {
        return NearbyFacility.of(1L, "STATION", "SW8", "마포역", walkMinutes * 52, walkMinutes, Instant.now());
    }
}
