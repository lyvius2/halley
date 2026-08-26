package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RegulationParamBootstrap implements ApplicationRunner {

    private static final String PROFILE = "2025-10-15";

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("ltv.rate", "0.4", RegulationValueType.DECIMAL, "LTV 비율"),
            new Seed("ltv.totalCap", "990000000", RegulationValueType.DECIMAL, "대출 총액 상한(원)"),
            new Seed("dsr.ratio", "0.4", RegulationValueType.DECIMAL, "DSR 한도(연간 원리금/연소득)"),
            new Seed("loan.interestRate", "0.04", RegulationValueType.DECIMAL, "기준 금리"),
            new Seed("loan.stressRate", "0.01", RegulationValueType.DECIMAL, "스트레스 가산 금리"),
            new Seed("loan.termYears", "30", RegulationValueType.INT, "대출 기간(년)"),
            new Seed("tax.acquisitionRate", "0.01", RegulationValueType.DECIMAL, "취득세율"),
            new Seed("tax.firstHomeDiscount", "0.5", RegulationValueType.DECIMAL, "생애최초 취득세 감면율"));

    private final RegulationParamRepository regulationParamRepository;

    public RegulationParamBootstrap(RegulationParamRepository regulationParamRepository) {
        this.regulationParamRepository = regulationParamRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!regulationParamRepository.findByProfile(PROFILE).isEmpty()) {
            return;
        }
        for (final Seed seed : DEFAULTS) {
            regulationParamRepository.save(new RegulationParam(
                    null, PROFILE, seed.key(), seed.value(), seed.valueType(),
                    seed.description(), null, null));
        }
        log.info("★ 규제 파라미터 {}건 시드 완료 (profile={}) ★", DEFAULTS.size(), PROFILE);
    }

    private record Seed(String key, String value, RegulationValueType valueType, String description) {
    }
}
