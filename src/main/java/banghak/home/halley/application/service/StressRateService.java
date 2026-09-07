package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.application.port.out.external.LoanRateHistoryPort;
import banghak.home.halley.domain.loan.RatePoint;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.StressRatePolicy;
import banghak.home.halley.domain.loan.StressRatePolicy.StressRateDecision;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** 기준 스트레스 금리를 한국은행 통계로 갱신한다. */
@Slf4j
@Service
public class StressRateService {

    private static final String STRESS_RATE_KEY = "loan.stressRate";
 /** 산출 근거를 남겨 둔다. 화면이 "왜 이 값인가"를 말할 수 있어야 한다. */
    private static final String SOURCE_KEY = "loan.stressRate.source";
    private static final String UPDATED_KEY = "loan.stressRate.updatedAt";
    private static final String PROFILE_KEY = "loan.regulation.profile";
    private static final String DEFAULT_PROFILE = "2025-10-15";
 /** 규제식이 보는 기간. 과거 5년. */
    private static final int LOOKBACK_YEARS = 5;

    private final LoanRateHistoryPort loanRateHistoryPort;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final BigDecimal floor;
    private final BigDecimal cap;

    public StressRateService(LoanRateHistoryPort loanRateHistoryPort,
                             RegulationParamRepository regulationParamRepository,
                             SystemConfigRepository systemConfigRepository,
                             @Value("${loan.stress-rate.floor:0.015}") BigDecimal floor,
                             @Value("${loan.stress-rate.cap:0.030}") BigDecimal cap) {
        this.loanRateHistoryPort = loanRateHistoryPort;
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.floor = floor;
        this.cap = cap;
    }

 /** 시계열을 받아 기준 스트레스 금리를 다시 정한다. */
    @Transactional
    public Optional<StressRateDecision> refresh() {
        if (!loanRateHistoryPort.isEnabled()) {
            log.info("Skipping stress rate refresh - ECOS not configured.");
            return Optional.empty();
        }
        final YearMonth to = YearMonth.now();
        final YearMonth from = to.minusYears(LOOKBACK_YEARS);
        final List<RatePoint> series = loanRateHistoryPort.fetchHouseholdLoanRates(from, to);

        final Optional<StressRateDecision> decided = StressRatePolicy.decide(series, floor, cap);
        if (decided.isEmpty()) {
            log.warn("Stress rate not refreshed - no usable series. period={}~{}, points={}",
                    from, to, series.size());
            return Optional.empty();
        }
        final StressRateDecision decision = decided.get();
        store(decision);
        log.info("Stress rate refreshed. value={}, {}", decision.stressRate(), decision.source());
        return decided;
    }

    private void store(StressRateDecision decision) {
        final String profile = activeProfile();
        regulationParamRepository.findByProfile(profile).stream()
                .filter(p -> STRESS_RATE_KEY.equals(p.paramKey()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> regulationParamRepository.update(new RegulationParam(
                                existing.id(), existing.profile(), existing.paramKey(),
                                decision.stressRate().toPlainString(), existing.valueType(),
                                existing.description(), null, Instant.now())),
                        () -> log.warn("Stress rate param not found in profile {} - skipped.", profile));
        putConfig(SOURCE_KEY, decision.source(), "스트레스 금리 산출 근거 (읽기 전용)");
        putConfig(UPDATED_KEY, Instant.now().toString(), "스트레스 금리 산출 시각 (읽기 전용)");
    }

    private void putConfig(String key, String value, String description) {
        systemConfigRepository.findById(key).ifPresentOrElse(
                existing -> systemConfigRepository.update(new SystemConfig(
                        existing.configKey(), value, existing.valueType(), existing.category(),
                        description, existing.masked(), null, Instant.now())),
                () -> systemConfigRepository.save(new SystemConfig(
                        key, value, ConfigValueType.STRING, ConfigCategory.LOAN,
                        description, false, null, Instant.now())));
    }

    private String activeProfile() {
        return systemConfigRepository.findById(PROFILE_KEY)
                .map(SystemConfig::configValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DEFAULT_PROFILE);
    }
}
