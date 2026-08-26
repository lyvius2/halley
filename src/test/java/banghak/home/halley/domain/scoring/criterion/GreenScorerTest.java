package banghak.home.halley.domain.scoring.criterion;

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
    @DisplayName("2km 내 공원·산·하천 3종이 있으면 100점")
    void allThreeKinds() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context(List.of(
                green("서울숲공원"), green("북한산"), green("청계천"))));

        // then
        assertThat(result.score()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("공원만 있으면 약 33.33점")
    void onlyPark() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(),
                TestContexts.context(List.of(green("서울숲공원"))));

        // then
        assertThat(result.score()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("녹색환경 데이터가 없으면 MISSING으로 기록된다")
    void missing() {
        // when
        final ScoreResult result = scorer.score(new PropertyBuilder().build(), TestContexts.context());

        // then
        assertThat(result.isComputed()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo("녹색환경 데이터 없음");
    }

    private static NearbyFacility green(String name) {
        return NearbyFacility.of(1L, "GREEN", "AT4", name, 800, 12, Instant.now());
    }
}
