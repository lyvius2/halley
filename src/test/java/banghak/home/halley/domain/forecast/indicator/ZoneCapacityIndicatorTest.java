package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("용도지역 지표 (설계 I131)")
class ZoneCapacityIndicatorTest {

    private final ZoneCapacityIndicator indicator = new ZoneCapacityIndicator(Map.of(
            "제1종일반주거지역", new BigDecimal("1.50"),
            "제3종일반주거지역", new BigDecimal("3.00")));

    @Test
    @DisplayName("포함된 용도지역 하나만 고른다 — 저촉·접함은 옆 필지의 것이다")
    void picksOnlyIncludedZone() {
        // given — 은마 실측처럼 1·2·3종이 함께 온다. 실제 적용은 '포함'인 3종뿐이다
        final var factor = indicator.evaluate(input(
                zone("제1종일반주거지역", LandUseConflict.ADJACENT),
                zone("제2종일반주거지역", LandUseConflict.OVERLAP),
                zone("제3종일반주거지역", LandUseConflict.INCLUDED))).orElseThrow();

        assertThat(factor.evidence()).contains("제3종일반주거지역").contains("300%");
        assertThat(factor.evidence()).doesNotContain("제1종").doesNotContain("제2종");
    }

    @Test
    @DisplayName("방향을 주지 않는다 — 상한만 알고 여유는 모른다")
    void doesNotGiveDirectionYet() {
        final var factor = indicator.evaluate(input(
                zone("제3종일반주거지역", LandUseConflict.INCLUDED))).orElseThrow();

        // 상한만 알고 '여유가 있다'고 말하면 없는 정보를 지어내는 것이다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("건축물대장 연동 전이라 미산출");
    }

    @Test
    @DisplayName("상한을 모르는 용도지역이면 모른다고 쓴다 — 임의의 값을 넣지 않는다")
    void saysSoWhenLimitUnknown() {
        final var factor = indicator.evaluate(input(
                zone("준주거지역", LandUseConflict.INCLUDED))).orElseThrow();

        assertThat(factor.evidence()).contains("준주거지역").contains("조례 확인 필요");
        assertThat(factor.evidence()).doesNotContain("상한 0%");
    }

    @Test
    @DisplayName("주거·상업이 아닌 항목은 용도지역이 아니다")
    void ignoresNonZoneItems() {
        assertThat(indicator.evaluate(input(
                zone("토지거래계약에관한허가구역", LandUseConflict.INCLUDED),
                zone("정비구역", LandUseConflict.INCLUDED)))).isEmpty();
    }

    @Test
    @DisplayName("토지이용계획이 없으면 내지 않는다")
    void skipsWithoutLandUse() {
        assertThat(indicator.evaluate(input())).isEmpty();
        assertThat(indicator.evaluate(new ForecastInput(null, List.of(), List.of(), List.of(), null)))
                .isEmpty();
    }

    private ForecastInput input(LandUse... items) {
        return new ForecastInput(null, List.of(), List.of(), List.of(), List.of(items));
    }

    private LandUse zone(String name, LandUseConflict conflict) {
        return new LandUse(null, 1L, "code", name, conflict, "4159710500105250000", Instant.now());
    }
}
