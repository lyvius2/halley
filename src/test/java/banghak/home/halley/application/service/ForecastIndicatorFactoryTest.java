package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.forecast.indicator.PriceIndicator;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("전망 지표 조립 (설계 I133)")
class ForecastIndicatorFactoryTest {

    @Autowired
    private ForecastIndicatorFactory factory;

    @Autowired
    private RegulationParamRepository regulationParamRepository;

    @Test
    @DisplayName("지표를 무거운 것부터 조립한다 — 순서가 곧 화면 순서다")
    void assemblesIndicatorsInOrder() {
        assertThat(factory.indicators())
                .extracting(PriceIndicator::code)
                .containsExactly("TRADE_TREND", "JEONSE_RATIO",
                        "LONG_TERM_TREND", "PEAK_DISTANCE", "RATE_CYCLE", "ZONE_CAPACITY");
    }

    @Test
    @DisplayName("용적률 상한을 regulation_param에서 읽는다 — 지자체 조례라 코드에 박으면 안 된다")
    void readsFarLimitsFromParams() {
        final var zone = factory.indicators().stream()
                .filter(i -> "ZONE_CAPACITY".equals(i.code()))
                .findFirst().orElseThrow();

        final var factor = zone.evaluate(new ForecastInput(null, List.of(), List.of(), List.of(),
                List.of(new LandUse(null, 1L, "c", "제3종일반주거지역",
                        LandUseConflict.INCLUDED, "p", Instant.now())), null)).orElseThrow();

        // 시드값 3.0 → 300%
        assertThat(factor.evidence()).contains("상한 300%");
    }

    @Test
    @DisplayName("임계값이 깨져 있으면 기본값을 쓴다 — 전망이 통째로 멈추는 것보다 낫다")
    void fallsBackWhenParamIsMalformed() {
        // given — 관리자가 실수로 이상한 값을 넣었다
        final var param = regulationParamRepository.findByProfile("2025-10-15").stream()
                .filter(p -> "forecast.trend.threshold".equals(p.paramKey()))
                .findFirst().orElseThrow();
        regulationParamRepository.update(new RegulationParam(param.id(), param.profile(),
                param.paramKey(), "이건 숫자가 아니다", RegulationValueType.DECIMAL,
                param.description(), null, Instant.now()));

        try {
            // when — 조립이 터지지 않아야 한다
            assertThat(factory.indicators()).hasSize(6);
        } finally {
            regulationParamRepository.update(new RegulationParam(param.id(), param.profile(),
                    param.paramKey(), param.paramValue(), param.valueType(),
                    param.description(), null, Instant.now()));
        }
    }

    @Test
    @DisplayName("예측기는 지표를 다 받아 조립된다")
    void buildsForecaster() {
        final var outlook = factory.forecaster().forecast(
                new ForecastInput(null, List.of(), List.of(), List.of(), List.of(), null));

        // 재료가 없으니 판단하지 않는다
        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(outlook.horizonMonths()).isEqualTo(12);
    }
}
