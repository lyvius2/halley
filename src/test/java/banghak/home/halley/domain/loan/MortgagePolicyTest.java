package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LTV 비율 판정 (설계 I66)")
class MortgagePolicyTest {

    private final RegulationParams params = RegulationParams.defaults();

    private static final Map<String, String> MATRIX = Map.ofEntries(
            Map.entry("ltv.rate.normal.none", "0.7"),
            Map.entry("ltv.rate.normal.one", "0.6"),
            Map.entry("ltv.rate.normal.multi", "0.6"),
            Map.entry("ltv.rate.adjustment.none", "0.5"),
            Map.entry("ltv.rate.adjustment.one", "0.3"),
            Map.entry("ltv.rate.adjustment.multi", "0"),
            Map.entry("ltv.rate.speculation.none", "0.4"),
            Map.entry("ltv.rate.speculation.one", "0.2"),
            Map.entry("ltv.rate.speculation.multi", "0"),
            Map.entry("ltv.rate.firstHome", "0.8"),
            Map.entry("ltv.cap.firstHome", "600000000"));

    @Test
    @DisplayName("지역이 강할수록 LTV가 낮아진다")
    void strongerZoneLowersLtv() {
        // when
        final BigDecimal normal = decide(RegulationZone.NORMAL, HouseOwnership.NONE).rate();
        final BigDecimal adjustment = decide(RegulationZone.ADJUSTMENT_TARGET, HouseOwnership.NONE).rate();
        final BigDecimal speculation = decide(RegulationZone.SPECULATION_OVERHEATED, HouseOwnership.NONE).rate();

        // then
        assertThat(normal).isGreaterThan(adjustment);
        assertThat(adjustment).isGreaterThan(speculation);
    }

    @Test
    @DisplayName("보유 주택이 많을수록 LTV가 낮아진다")
    void moreHousesLowerLtv() {
        // when
        final BigDecimal none = decide(RegulationZone.ADJUSTMENT_TARGET, HouseOwnership.NONE).rate();
        final BigDecimal one = decide(RegulationZone.ADJUSTMENT_TARGET, HouseOwnership.ONE).rate();
        final BigDecimal multi = decide(RegulationZone.ADJUSTMENT_TARGET, HouseOwnership.MULTI).rate();

        // then
        assertThat(none).isGreaterThan(one);
        assertThat(one).isGreaterThan(multi);
    }

    @Test
    @DisplayName("규제지역 다주택은 LTV 0% — 왜 막혔는지 이유를 남긴다")
    void multiHouseInRegulatedZoneIsBlocked() {
        // when
        final LtvDecision decision = decide(RegulationZone.SPECULATION_OVERHEATED, HouseOwnership.MULTI);

        // then — 숫자만 0이면 고장으로 읽힌다
        assertThat(decision.rate()).isEqualByComparingTo("0");
        assertThat(decision.reason()).contains("투기과열지구").contains("다주택").contains("제한");
    }

    @Test
    @DisplayName("생애최초는 지역·보유와 무관하게 우대 비율을 쓰고 별도 상한이 붙는다")
    void firstHomeOverridesZoneAndOwnership() {
        // when — 투기과열지구 다주택인데도 생애최초면 우대
        final LtvDecision decision = MortgagePolicy.decide(
                RegulationZone.SPECULATION_OVERHEATED, HouseOwnership.MULTI, true, MATRIX, params);

        // then
        assertThat(decision.rate()).isEqualByComparingTo("0.8");
        assertThat(decision.cap()).isEqualTo(600_000_000L);
        assertThat(decision.reason()).contains("생애최초").contains("6억");
    }

    @Test
    @DisplayName("파라미터가 없으면 프로파일 기본 비율로 떨어진다 — 0%로 막지 않는다")
    void fallsBackToDefaultRateWhenParameterMissing() {
        // when — 매트릭스가 비어 있다
        final LtvDecision decision = MortgagePolicy.decide(
                RegulationZone.ADJUSTMENT_TARGET, HouseOwnership.ONE, false, Map.of(), params);

        // then — 규정을 모르는 상태에서 0%로 막으면 화면이 고장난 것처럼 보인다
        assertThat(decision.rate()).isEqualByComparingTo(params.ltvRate());
        assertThat(decision.rate().signum()).isPositive();
    }

    @Test
    @DisplayName("값이 숫자가 아니면 기본 비율로 떨어진다")
    void toleratesMalformedParameter() {
        // when
        final LtvDecision decision = MortgagePolicy.decide(
                RegulationZone.NORMAL, HouseOwnership.NONE, false,
                Map.of("ltv.rate.normal.none", "칠십퍼센트"), params);

        // then
        assertThat(decision.rate()).isEqualByComparingTo(params.ltvRate());
    }

    @Test
    @DisplayName("이유에 지역·보유·비율이 함께 들어간다")
    void reasonExplainsTheRate() {
        // when
        final LtvDecision decision = decide(RegulationZone.NORMAL, HouseOwnership.NONE);

        // then
        assertThat(decision.reason()).contains("비규제지역").contains("무주택").contains("70%");
        assertThat(decision.zone()).isEqualTo(RegulationZone.NORMAL);
    }

    private LtvDecision decide(RegulationZone zone, HouseOwnership ownership) {
        return MortgagePolicy.decide(zone, ownership, false, MATRIX, params);
    }
}
