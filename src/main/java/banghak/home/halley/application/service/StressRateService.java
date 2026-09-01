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

/**
 * 기준 스트레스 금리를 한국은행 통계로 갱신한다 (설계 I116).
 *
 * <p>지금까지 `loan.stressRate`는 <b>사람이 넣은 고정값</b>(하한 1.5%)이었습니다. 실제 규제식은
 * 과거 5년 시계열이 필요해 미뤄 뒀던 것을, ECOS 연동으로 자동화합니다.
 *
 * <p><b>못 받으면 아무것도 하지 않습니다.</b> 기존 값을 그대로 둡니다 — 조회 실패로 스트레스가
 * 0이 되면 한도가 실제보다 넉넉하게 나옵니다. 조용히 낙관적으로 틀리는 쪽이 가장 위험합니다.
 */
@Slf4j
@Service
public class StressRateService {

    private static final String STRESS_RATE_KEY = "loan.stressRate";
    /** 산출 근거를 남겨 둔다. 화면이 "왜 이 값인가"를 말할 수 있어야 한다 (설계 I81과 같은 이유). */
    private static final String SOURCE_KEY = "loan.stressRate.source";
    private static final String UPDATED_KEY = "loan.stressRate.updatedAt";
    private static final String PROFILE_KEY = "loan.regulation.profile";
    private static final String DEFAULT_PROFILE = "2025-10-15";
    /** 규제식이 보는 기간 — 과거 5년. */
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

    /**
     * 시계열을 받아 기준 스트레스 금리를 다시 정한다.
     *
     * @return 갱신했으면 그 근거. 조회 실패·자료 부족이면 {@code empty}이고 기존 값은 그대로다
     */
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
            // 기존 값을 그대로 둔다. 0으로 떨어뜨리면 한도가 넉넉해진다
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
        // 근거는 시스템 설정에 둔다. 규제 파라미터는 숫자만 담는 자리다.
        // 두 키의 설명이 같으면 화면에 <b>같은 이름이 두 줄</b> 뜬다 (설계 I185)
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
