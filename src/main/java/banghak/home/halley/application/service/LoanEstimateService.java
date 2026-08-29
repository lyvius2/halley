package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.domain.property.ReferenceTransaction;
import banghak.home.halley.domain.loan.CollateralValuation;
import banghak.home.halley.domain.loan.CollateralValuator;
import banghak.home.halley.domain.loan.HouseOwnership;
import banghak.home.halley.domain.loan.LoanEstimateInput;
import banghak.home.halley.domain.loan.LtvDecision;
import banghak.home.halley.domain.loan.MortgagePolicy;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.loan.TradeSample;

import java.time.LocalDate;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.loan.LoanEstimate;
import banghak.home.halley.domain.loan.LoanEstimateResult;
import banghak.home.halley.domain.loan.ProductType;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationParams;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.setting.SystemConfig;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LoanEstimateService {

    private static final String PROFILE_KEY = "loan.regulation.profile";
    private static final String DEFAULT_PROFILE = "2025-10-15";

    private final PropertyRepository propertyRepository;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final RegulatedAreaService regulatedAreaService;
    private final UserRepository userRepository;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final LoanEstimateRepository loanEstimateRepository;
    private final ObjectMapper objectMapper;

    public LoanEstimateService(PropertyRepository propertyRepository,
                               ReferenceTransactionRepository referenceTransactionRepository,
                               RegulatedAreaService regulatedAreaService,
                               UserRepository userRepository,
                               RegulationParamRepository regulationParamRepository,
                               SystemConfigRepository systemConfigRepository,
                               LoanEstimateRepository loanEstimateRepository,
                               ObjectMapper objectMapper) {
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.regulatedAreaService = regulatedAreaService;
        this.userRepository = userRepository;
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.loanEstimateRepository = loanEstimateRepository;
        this.objectMapper = objectMapper;
    }

    public LoanEstimateResponse estimate(Long propertyId, LoanEstimateRequest request) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        final long askingPrice = property.priceDeposit() == null ? 0L : property.priceDeposit();
        final Map<String, String> rawParams = loadRawParams();
        final RegulationParams params = loadParams(rawParams);

        // 입력이 비면 로그인 사용자의 프로필로 채운다 — 모달을 열자마자 결과가 보여야 한다 (설계 I55)
        final Optional<User> me = currentUser();
        final long annualIncome = orProfile(request.annualIncome(), me.map(User::annualIncomeOrZero));
        final long cash = orProfile(request.cash(), me.map(User::cashOrZero));
        final long existingLoan = orProfile(request.existingLoan(), me.map(User::existingLoanOrZero));
        final boolean firstHome = Boolean.TRUE.equals(request.firstHome());
        final boolean insured = Boolean.TRUE.equals(request.mortgageInsured());

        // LTV는 호가가 아니라 담보가치에 매긴다 (설계 I64-1)
        final CollateralValuation collateral = CollateralValuator.estimate(
                property.kbPrice(), tradeSamples(propertyId), property.areaExclusiveM2(),
                property.officialPrice(), askingPrice, params.officialPriceRatio(), LocalDate.now());

        // 규제지역·주택 보유 수로 LTV 비율을 정한다 (설계 I66)
        final RegulationZone zone = regulatedAreaService.resolve(property);
        final HouseOwnership ownership = HouseOwnership.of(request.ownedHouseCount());
        final LtvDecision ltv = MortgagePolicy.decide(zone, ownership, firstHome, rawParams, params);

        final LoanEstimateResult result = new LoanCalculator(ltv.rate(), ltv.cap())
                .estimate(new LoanEstimateInput(askingPrice, collateral, annualIncome, cash,
                        existingLoan, firstHome, insured), withLtv(params, ltv));

        loanEstimateRepository.save(new LoanEstimate(
                null, propertyId, ProductType.MORTGAGE, ltv.rate(),
                result.ltvLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), result.acquisitionTax(),
                assumptions(annualIncome, cash, existingLoan, firstHome, insured, result,
                        zone, ownership, ltv),
                Instant.now()));

        return new LoanEstimateResponse(
                propertyId, result.ltvLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), result.acquisitionTax(), result.monthlyPayment(),
                askingPrice, annualIncome, cash, existingLoan,
                result.dsrCapacity(), result.existingLoanAnnual(),
                result.collateralValue(), result.collateralSource(),
                result.collateralSource().label(),
                result.collateralSampleCount(), result.collateralReliable(),
                result.leaseDeduction(), insured,
                zone, zone.label(), ownership, ownership.label(),
                ltv.rate(), ltv.reason(),
                result.monthlyRate(), result.termMonths());
    }

    public List<LoanEstimateHistoryResponse> history(Long propertyId) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return loanEstimateRepository.findByPropertyId(propertyId).stream()
                .map(e -> new LoanEstimateHistoryResponse(
                        e.propertyId(), e.ltvLimit(), e.dsrLimit(), e.finalLimit(),
                        e.requiredCash(), e.acquisitionTax(), e.computedAt()))
                .toList();
    }

    /** 판정된 LTV 비율·상한을 계산기가 쓰도록 갈아 끼운다. 다른 수치는 프로파일 그대로다. */
    private RegulationParams withLtv(RegulationParams params, LtvDecision ltv) {
        return new RegulationParams(
                ltv.rate(), ltv.cap(), params.dsrRatio(), params.interestRate(), params.stressRate(),
                params.termYears(), params.acquisitionTaxRate(), params.firstHomeDiscount(),
                params.leaseDeduction(), params.officialPriceRatio());
    }

    /** 활성 프로파일의 원본 키·값. LTV 매트릭스처럼 레코드에 담기 어려운 값은 여기서 직접 읽는다. */
    private Map<String, String> loadRawParams() {
        final String profile = systemConfigRepository.findById(PROFILE_KEY)
                .map(SystemConfig::configValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(DEFAULT_PROFILE);
        return regulationParamRepository.findByProfile(profile).stream()
                .collect(Collectors.toMap(RegulationParam::paramKey, RegulationParam::paramValue));
    }

    private RegulationParams loadParams(Map<String, String> values) {
        final RegulationParams defaults = RegulationParams.defaults();
        return new RegulationParams(
                decimal(values, "ltv.rate", defaults.ltvRate()),
                longValue(values, "ltv.totalCap", defaults.totalCap()),
                decimal(values, "dsr.ratio", defaults.dsrRatio()),
                decimal(values, "loan.interestRate", defaults.interestRate()),
                decimal(values, "loan.stressRate", defaults.stressRate()),
                intValue(values, "loan.termYears", defaults.termYears()),
                decimal(values, "tax.acquisitionRate", defaults.acquisitionTaxRate()),
                decimal(values, "tax.firstHomeDiscount", defaults.firstHomeDiscount()),
                longValue(values, "ltv.leaseDeduction", defaults.leaseDeduction()),
                decimal(values, "valuation.officialPriceRatio", defaults.officialPriceRatio()));
    }

    /**
     * 담보가치를 매길 재료 — 이 매물의 최근 실거래가. 이미 수집해 둔 캐시만 읽고 국토부를 부르지 않는다
     * (대출 계산이 외부 API 지연에 묶이면 안 된다).
     */
    private List<TradeSample> tradeSamples(Long propertyId) {
        return referenceTransactionRepository.findByPropertyId(propertyId).stream()
                .filter(t -> t.price() != null && t.price() > 0)
                .map(t -> new TradeSample(t.price(), t.areaM2(), t.contractDate()))
                .toList();
    }

    /** 어떤 전제로 계산했는지 남긴다. 나중에 값이 왜 그랬는지 되짚을 수 있어야 한다. */
    private ObjectNode assumptions(long annualIncome, long cash, long existingLoan,
                                   boolean firstHome, boolean insured, LoanEstimateResult result,
                                   RegulationZone zone, HouseOwnership ownership, LtvDecision ltv) {
        return objectMapper.createObjectNode()
                .put("annualIncome", annualIncome)
                .put("cash", cash)
                .put("existingLoan", existingLoan)
                .put("firstHome", firstHome)
                .put("mortgageInsured", insured)
                .put("collateralValue", result.collateralValue())
                .put("collateralSource", result.collateralSource().name())
                .put("leaseDeduction", result.leaseDeduction())
                .put("collateralSampleCount", result.collateralSampleCount())
                .put("zone", zone.name())
                .put("ownership", ownership.name())
                .put("ltvRate", ltv.rate().toPlainString());
    }

    private long orProfile(Long requested, Optional<Long> fromProfile) {
        if (requested != null) {
            return requested;
        }
        return fromProfile.orElse(0L);
    }

    private Optional<User> currentUser() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return userRepository.findById(principal.getId());
        }
        return Optional.empty();
    }

    private static BigDecimal decimal(Map<String, String> values, String key, BigDecimal fallback) {
        try {
            return values.containsKey(key) ? new BigDecimal(values.get(key)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        try {
            return values.containsKey(key) ? Long.parseLong(values.get(key)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try {
            return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
