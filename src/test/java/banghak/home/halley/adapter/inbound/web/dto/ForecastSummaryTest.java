package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceForecast;
import banghak.home.halley.domain.forecast.PriceOutlook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("목록에 싣는 전망 요약 (설계 I136 · I142)")
class ForecastSummaryTest {

    @Test
    @DisplayName("낸 적 없으면 stored=false — 매매가를 눌러 분석시킬 수 있다")
    void pendingIsNotStored() {
        assertThat(ForecastSummary.pending(false).stored()).isFalse();
        assertThat(ForecastSummary.pending(true).stored()).isFalse();
    }

    @Test
    @DisplayName("'모르겠다'도 낸 것이다 — UNCERTAIN 이라도 stored=true")
    void uncertainVerdictIsStored() {
        // 이것이 이 필드를 만든 이유다. direction 만 보면 pending 과 구분되지 않는다
        final PriceForecast uncertain = new PriceForecast(1L, 1L,
                new PriceOutlook(ForecastDirection.UNCERTAIN, ForecastConfidence.LOW, 12,
                        List.of(), List.of()),
                null, ForecastDirection.UNCERTAIN, "hash", "claude", Instant.now());

        final ForecastSummary summary = ForecastSummary.from(uncertain, false);

        assertThat(summary.direction()).isEqualTo(ForecastSummary.pending(false).direction());
        assertThat(summary.stored()).isTrue();
    }

    @Test
    @DisplayName("분석 중인지와 낸 적 있는지는 따로다")
    void runningAndStoredAreIndependent() {
        final PriceForecast stored = new PriceForecast(1L, 1L,
                new PriceOutlook(ForecastDirection.UP, ForecastConfidence.HIGH, 12,
                        List.of(), List.of()),
                null, ForecastDirection.UP, "hash", "claude", Instant.now());

        // 낸 적이 있는데 지금 또 돌고 있을 수 있다 (월간 재계산 · 다시 분석)
        assertThat(ForecastSummary.from(stored, true).stored()).isTrue();
        assertThat(ForecastSummary.from(stored, true).running()).isTrue();
        // 아직 낸 적 없는데 지금 처음 돌고 있을 수 있다 (매매가 클릭 직후)
        assertThat(ForecastSummary.pending(true).stored()).isFalse();
        assertThat(ForecastSummary.pending(true).running()).isTrue();
    }
}
