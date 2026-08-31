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
            new Seed("loan.stressRate", "0.015", RegulationValueType.DECIMAL,
                    "스트레스 기준 금리 (하한 1.5%)"),
            new Seed("loan.stressApplyRatio", "1.0", RegulationValueType.DECIMAL,
                    "스트레스 DSR 단계 적용률 (2025.7~ 100%)"),
            new Seed("loan.termYears", "30", RegulationValueType.INT, "대출 기간(년)"),
            new Seed("tax.acquisitionRate", "0.01", RegulationValueType.DECIMAL, "취득세율"),
            new Seed("tax.firstHomeDiscount", "0.5", RegulationValueType.DECIMAL, "생애최초 취득세 감면율"),
            // 방공제·현실화율은 시행령·고시로 바뀐다. 값의 근거와 기준일을 설명에 남긴다 (설계 I64)
            new Seed("ltv.leaseDeduction", "55000000", RegulationValueType.DECIMAL,
                    "방공제(소액임차보증금 최우선변제금, 원) — 서울 기준. "
                            + "주택임대차보호법 시행령 개정 시 갱신 필요. MCI/MCG 가입 시 미차감"),
            new Seed("valuation.officialPriceRatio", "0.7", RegulationValueType.DECIMAL,
                    "공시가격 현실화율 — 공시가격을 담보가치로 환산할 때 나누는 값. 매년 갱신 필요"),
            // 가격 전망의 요인 판정 임계값 (설계 I133).
            // 전부 임의의 값이다 — 결론도 화살표도 정하지 않고 모달의 참고 문구에만
            // 영향을 주므로 위험은 작지만, 사후 검증(구현 10)에서 조정할 거리다
            new Seed("forecast.trend.threshold", "0.02", RegulationValueType.DECIMAL,
                    "실거래 추세를 방향으로 읽는 최소 변동률. 부동산 월간 변동의 잡음이 이 정도라 잡았다"),
            new Seed("forecast.jeonse.high", "0.70", RegulationValueType.DECIMAL,
                    "이 위면 실거주 수요가 하방을 받친다고 본다"),
            new Seed("forecast.jeonse.low", "0.50", RegulationValueType.DECIMAL,
                    "이 아래면 매매가에 기대가 많이 실려 있다고 본다"),
            // 용적률 상한은 지자체 조례다 (설계 I132). 지역마다 달라 반드시 확인 후 조정
            new Seed("forecast.far.제1종일반주거지역", "1.5", RegulationValueType.DECIMAL,
                    "용적률 상한 — 지자체 조례. 서울 기준"),
            new Seed("forecast.far.제2종일반주거지역", "2.5", RegulationValueType.DECIMAL,
                    "용적률 상한 — 지자체 조례. 서울 기준"),
            new Seed("forecast.far.제3종일반주거지역", "3.0", RegulationValueType.DECIMAL,
                    "용적률 상한 — 지자체 조례. 서울 기준"),
            new Seed("forecast.far.준주거지역", "4.0", RegulationValueType.DECIMAL,
                    "용적률 상한 — 지자체 조례. 서울 기준"),
            // LTV 매트릭스 (설계 I66) — 지역 × 보유주택. 고시로 자주 바뀌므로 반드시 확인 후 조정
            new Seed("ltv.rate.normal.none", "0.7", RegulationValueType.DECIMAL, "비규제·무주택 LTV"),
            new Seed("ltv.rate.normal.one", "0.6", RegulationValueType.DECIMAL, "비규제·1주택 LTV"),
            new Seed("ltv.rate.normal.multi", "0.6", RegulationValueType.DECIMAL, "비규제·다주택 LTV"),
            new Seed("ltv.rate.adjustment.none", "0.5", RegulationValueType.DECIMAL, "조정대상지역·무주택 LTV"),
            new Seed("ltv.rate.adjustment.one", "0.3", RegulationValueType.DECIMAL, "조정대상지역·1주택 LTV"),
            new Seed("ltv.rate.adjustment.multi", "0", RegulationValueType.DECIMAL, "조정대상지역·다주택 LTV"),
            new Seed("ltv.rate.speculation.none", "0.4", RegulationValueType.DECIMAL, "투기과열지구·무주택 LTV"),
            new Seed("ltv.rate.speculation.one", "0.2", RegulationValueType.DECIMAL, "투기과열지구·1주택 LTV"),
            new Seed("ltv.rate.speculation.multi", "0", RegulationValueType.DECIMAL, "투기과열지구·다주택 LTV"),
            new Seed("ltv.rate.firstHome", "0.8", RegulationValueType.DECIMAL,
                    "생애최초 우대 LTV — 지역·보유와 무관하게 적용"),
            new Seed("ltv.cap.firstHome", "600000000", RegulationValueType.DECIMAL,
                    "생애최초 대출 총액 상한(원)"),
            // 전세자금대출 (설계 I67) — 보증기관·정책마다 다르고 자주 바뀐다
            new Seed("jeonse.guaranteeRate", "0.8", RegulationValueType.DECIMAL,
                    "전세자금대출 보증비율 — 보증금의 몇 %까지 보증하는지"),
            new Seed("jeonse.guaranteeCap", "222000000", RegulationValueType.DECIMAL,
                    "전세자금대출 보증기관 한도(원) — HUG/HF/SGI별로 다르다. 확인 후 조정"),
            new Seed("jeonse.interestRate", "0.04", RegulationValueType.DECIMAL, "전세자금대출 기준 금리"),
            new Seed("jeonse.termYears", "2", RegulationValueType.INT,
                    "전세자금대출 기간(년) — 전세 계약 주기와 같다"));

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
        log.info("Seeded {} regulation parameters (profile={}). "
                        + "LTV/lease-deduction values are defaults - verify against the current notice.",
                DEFAULTS.size(), PROFILE);
    }

    private record Seed(String key, String value, RegulationValueType valueType, String description) {
    }
}
