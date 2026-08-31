package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.domain.forecast.RuleBasedForecaster;
import banghak.home.halley.domain.forecast.indicator.JeonseRatioIndicator;
import banghak.home.halley.domain.forecast.indicator.PriceIndicator;
import banghak.home.halley.domain.forecast.indicator.RateCycleIndicator;
import banghak.home.halley.domain.forecast.indicator.TradeTrendIndicator;
import banghak.home.halley.domain.forecast.indicator.ZoneCapacityIndicator;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지표와 예측기를 조립한다 (설계 I133).
 *
 * <p><b>임계값을 매번 읽습니다.</b> 관리자가 `regulation_param`을 바꾸면 다음 전망부터
 * 반영돼야 합니다 — 빈 생성 시점에 굳히면 재기동해야 바뀝니다.
 *
 * <p>지표는 `ScoringConfig`가 스코어러를 조립하는 것과 같은 방식으로 만듭니다 —
 * 도메인은 순수 클래스로 두고 조립만 여기서 합니다.
 */
@Slf4j
@Component
public class ForecastIndicatorFactory {

    private static final String PROFILE_KEY = "loan.regulation.profile";
    private static final String DEFAULT_PROFILE = "2025-10-15";
    private static final String FAR_PREFIX = "forecast.far.";

    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final int horizonMonths;
    private final int redevelopmentAgeYears;

    public ForecastIndicatorFactory(RegulationParamRepository regulationParamRepository,
                                    SystemConfigRepository systemConfigRepository,
                                    @Value("${forecast.horizon-months:12}") int horizonMonths,
                                    @Value("${forecast.redevelopment-age-years:30}")
                                    int redevelopmentAgeYears) {
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.horizonMonths = horizonMonths;
        this.redevelopmentAgeYears = redevelopmentAgeYears;
    }

    public RuleBasedForecaster forecaster() {
        return new RuleBasedForecaster(indicators(), horizonMonths);
    }

    public int horizonMonths() {
        return horizonMonths;
    }

    /**
     * 순서가 곧 화면 순서입니다. <b>무거운 것부터</b> 둡니다 — 사용자가 위에서부터 읽습니다.
     */
    public List<PriceIndicator> indicators() {
        final Map<String, String> params = loadParams();
        return List.of(
                new TradeTrendIndicator(decimal(params, "forecast.trend.threshold", "0.02")),
                new JeonseRatioIndicator(
                        decimal(params, "forecast.jeonse.high", "0.70"),
                        decimal(params, "forecast.jeonse.low", "0.50")),
                new RateCycleIndicator(),
                new ZoneCapacityIndicator(farLimits(params), redevelopmentAgeYears));
    }

    private Map<String, String> loadParams() {
        final Map<String, String> values = new HashMap<>();
        for (final RegulationParam param : regulationParamRepository.findByProfile(activeProfile())) {
            values.put(param.paramKey(), param.paramValue());
        }
        return values;
    }

    /** `forecast.far.제3종일반주거지역` → `제3종일반주거지역`. */
    private Map<String, BigDecimal> farLimits(Map<String, String> params) {
        final Map<String, BigDecimal> limits = new HashMap<>();
        params.forEach((key, value) -> {
            if (!key.startsWith(FAR_PREFIX)) {
                return;
            }
            try {
                limits.put(key.substring(FAR_PREFIX.length()), new BigDecimal(value));
            } catch (RuntimeException e) {
                // 하나가 깨져도 나머지 용도지역은 살린다
                log.warn("Ignoring malformed FAR limit. key={}, value={}", key, value);
            }
        });
        return limits;
    }

    /**
     * 값이 없거나 깨졌으면 기본값을 씁니다. <b>전망이 통째로 멈추는 것보다 낫습니다</b> —
     * 임계값은 요인의 방향만 가르고 결론을 정하지 않습니다.
     */
    private BigDecimal decimal(Map<String, String> params, String key, String fallback) {
        final String value = params.get(key);
        if (value == null || value.isBlank()) {
            return new BigDecimal(fallback);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Malformed forecast param - using default. key={}, value={}, default={}",
                    key, value, fallback);
            return new BigDecimal(fallback);
        }
    }

    private String activeProfile() {
        return systemConfigRepository.findById(PROFILE_KEY)
                .map(SystemConfig::configValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DEFAULT_PROFILE);
    }
}
