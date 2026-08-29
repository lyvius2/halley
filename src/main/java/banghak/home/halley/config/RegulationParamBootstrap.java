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
            new Seed("tax.firstHomeDiscount", "0.5", RegulationValueType.DECIMAL, "생애최초 취득세 감면율"),
            // 방공제·현실화율은 시행령·고시로 바뀐다. 값의 근거와 기준일을 설명에 남긴다 (설계 I64)
            new Seed("ltv.leaseDeduction", "55000000", RegulationValueType.DECIMAL,
                    "방공제(소액임차보증금 최우선변제금, 원) — 서울 기준. "
                            + "주택임대차보호법 시행령 개정 시 갱신 필요. MCI/MCG 가입 시 미차감"),
            new Seed("valuation.officialPriceRatio", "0.7", RegulationValueType.DECIMAL,
                    "공시가격 현실화율 — 공시가격을 담보가치로 환산할 때 나누는 값. 매년 갱신 필요"));

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
        log.info("Seeded {} regulation parameters (profile={}).", DEFAULTS.size(), PROFILE);
    }

    private record Seed(String key, String value, RegulationValueType valueType, String description) {
    }
}
