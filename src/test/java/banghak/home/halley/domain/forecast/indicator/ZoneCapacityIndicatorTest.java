package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.building.BuildingLedger;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("용도지역 지표 (설계 I131)")
class ZoneCapacityIndicatorTest {

    private static final Map<String, BigDecimal> LIMITS = Map.of(
            "제1종일반주거지역", new BigDecimal("1.50"),
            "제3종일반주거지역", new BigDecimal("3.00"));

    private final ZoneCapacityIndicator indicator = new ZoneCapacityIndicator(LIMITS, 30);

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
    @DisplayName("대장을 못 받으면 여유를 말하지 않는다 — 근사값으로 채우지 않는다")
    void noHeadroomWithoutLedger() {
        final var factor = indicator.evaluate(input(
                zone("제3종일반주거지역", LandUseConflict.INCLUDED))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("건축물대장을 못 받아 미산출");
    }

    @Test
    @DisplayName("연식이 차고 여유가 있으면 UP — 재건축 여력")
    void oldAndRoomyMeansUp() {
        // given — 1988년 준공, 현재 용적률 180%, 상한 300% → 여유 120%p
        final var factor = indicator.evaluate(withLedger(
                ledger("180", LocalDate.of(1988, 5, 1)))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.UP);
        assertThat(factor.evidence()).contains("여유 120%p").contains("상한 300%").contains("현재 180%");
    }

    @Test
    @DisplayName("여유가 커도 신축이면 방향을 주지 않는다 — 재건축은 수십 년 뒤 이야기다")
    void newBuildingGetsNoDirection() {
        // given — 실측한 동탄역시범호반써밋: 2015년 준공, 용적률 173.34%
        final var factor = indicator.evaluate(withLedger(
                ledger("173.34", LocalDate.of(2015, 2, 12)))).orElseThrow();

        // 여유는 127%p나 되지만 재건축 호재가 아니다
        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("재건축 논의 시점은 아님");
        assertThat(factor.evidence()).doesNotContain("여유");
    }

    @Test
    @DisplayName("연식이 찼어도 여유가 없으면 방향을 주지 않는다")
    void oldButFullGetsNoDirection() {
        // given — 1988년 준공인데 이미 상한을 다 썼다
        final var factor = indicator.evaluate(withLedger(
                ledger("300", LocalDate.of(1988, 5, 1)))).orElseThrow();

        assertThat(factor.effect()).isEqualTo(ForecastDirection.FLAT);
        assertThat(factor.evidence()).contains("여유 0%p");
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
        assertThat(indicator.evaluate(new ForecastInput(null, List.of(), List.of(), List.of(), null, null)))
                .isEmpty();
    }

    private ForecastInput input(LandUse... items) {
        return new ForecastInput(null, List.of(), List.of(), List.of(), List.of(items), null);
    }

    private ForecastInput withLedger(BuildingLedger ledger) {
        return new ForecastInput(null, List.of(), List.of(), List.of(),
                List.of(zone("제3종일반주거지역", LandUseConflict.INCLUDED)), ledger);
    }

    private BuildingLedger ledger(String farRatio, LocalDate approvedOn) {
        return new BuildingLedger("단지", new BigDecimal("64303"), new BigDecimal(farRatio),
                new BigDecimal("15.71"), 1002, 16, 1328, approvedOn);
    }

    private LandUse zone(String name, LandUseConflict conflict) {
        return new LandUse(null, 1L, "code", name, conflict, "4159710500105250000", Instant.now());
    }
}
