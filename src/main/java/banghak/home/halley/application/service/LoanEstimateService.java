package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ReferenceTransaction;
import banghak.home.halley.domain.loan.CollateralValuation;
import banghak.home.halley.domain.loan.CollateralValuator;
import banghak.home.halley.domain.loan.HouseOwnership;
import banghak.home.halley.domain.loan.JeonseEstimateInput;
import banghak.home.halley.domain.loan.JeonseEstimateResult;
import banghak.home.halley.domain.loan.JeonseLoanCalculator;
import banghak.home.halley.domain.loan.JeonsePolicy;
import banghak.home.halley.domain.loan.JeonseTerms;
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
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.MarketRate;
import banghak.home.halley.adapter.outbound.persistence.UserDebtRepository;
import banghak.home.halley.domain.loan.ExistingDebt;
import banghak.home.halley.domain.loan.RateType;
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
    private final PropertyAccessGuard propertyAccessGuard;
    private final ReferenceTransactionRepository referenceTransactionRepository;
    private final ComplexService complexService;
 /** 실거래를 고를 때의 면적 허용 범위. ReferenceTransactionService 와 같은 값이어야 한다. */
    static final double REFERENCE_AREA_TOLERANCE = 0.15;
    private final RegulatedAreaService regulatedAreaService;
    private final UserRepository userRepository;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final LoanEstimateRepository loanEstimateRepository;
    private final RegulationNoticeService regulationNoticeService;
    private final MarketRateService marketRateService;
    private final UserDebtRepository userDebtRepository;
    private final ObjectMapper objectMapper;

    public LoanEstimateService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                               ReferenceTransactionRepository referenceTransactionRepository,
                               RegulatedAreaService regulatedAreaService,
                               UserRepository userRepository,
                               RegulationParamRepository regulationParamRepository,
                               SystemConfigRepository systemConfigRepository,
                               LoanEstimateRepository loanEstimateRepository,
                               RegulationNoticeService regulationNoticeService,
                               MarketRateService marketRateService,
                               UserDebtRepository userDebtRepository,
                               ComplexService complexService,
                               ObjectMapper objectMapper) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.referenceTransactionRepository = referenceTransactionRepository;
        this.regulatedAreaService = regulatedAreaService;
        this.userRepository = userRepository;
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.loanEstimateRepository = loanEstimateRepository;
        this.regulationNoticeService = regulationNoticeService;
        this.marketRateService = marketRateService;
        this.userDebtRepository = userDebtRepository;
        this.complexService = complexService;
        this.objectMapper = objectMapper;
    }

 /** 규제지역 값을 못 믿을 때 화면에 실을 문구. */
 /** 로그인 사용자의 종류별 기존 부채. */
    private List<ExistingDebt> myDebts() {
        return currentUser().map(u -> userDebtRepository.findByUserId(u.id())).orElseGet(List::of);
    }

    private String zoneWarning() {
        if (regulationNoticeService.isTrustworthy()) {
            return null;
        }
        return "규제지역 정보를 아직 불러오지 못했습니다. 실제 규제지역이라면 한도가 과대평가될 수 있습니다.";
    }

 /** 매물의 거래유형에 맞는 대출을 산정한다. */
    public LoanEstimateResponse estimate(Long propertyId, LoanEstimateRequest request) {
        final Property property = propertyAccessGuard.require(propertyId);
        final long price = property.priceDeposit() == null ? 0L : property.priceDeposit();
        final Map<String, String> rawParams = loadRawParams();
        final boolean jeonse = property.dealType() != DealType.SALE;
        final Optional<MarketRate> marketRate = marketRateService.find(
                jeonse ? LoanProductType.JEONSE : LoanProductType.MORTGAGE);
        final RegulationParams params = withMarketRate(loadParams(rawParams), marketRate);

        final Optional<User> me = currentUser();
        final long annualIncome = orProfile(request.annualIncome(), me.map(User::annualIncomeOrZero));
        final long cash = orProfile(request.cash(), me.map(User::cashOrZero));
        final long existingLoan = orProfile(request.existingLoan(), me.map(User::existingLoanOrZero));

        final long groupCash = groupCash(property.groupId());

        if (jeonse) {
            return estimateJeonse(propertyId, price, annualIncome, cash, existingLoan, groupCash,
                    rawParams, params, marketRate);
        }
        return estimateMortgage(propertyId, property, price, annualIncome, cash, existingLoan,
                groupCash, request, rawParams, params, marketRate);
    }

 /** 시장 금리를 받았으면 interestRate만 갈아 끼운다. */
    private RegulationParams withMarketRate(RegulationParams params, Optional<MarketRate> marketRate) {
        return marketRate
                .map(rate -> new RegulationParams(
                        params.ltvRate(), params.totalCap(), params.dsrRatio(),
                        rate.rate(), params.stressRate(), params.termYears(),
                        params.acquisitionTaxRate(), params.firstHomeDiscount(),
                        params.leaseDeduction(), params.officialPriceRatio(), params.stressApplyRatio()))
                .orElse(params);
    }

 /** 금리가 어디서 왔는지. 못 받아 기본값으로 떨어졌으면 그 사실을 밝힙니다. * 조용히 다른 값을 쓰면 사용자는 검증할 수 없습니다. */
 /** 스트레스 금리의 출처. 한국은행 통계로 산출했으면 그 근거가 담겨 있고, */
    private String stressRateSource() {
        return systemConfigRepository.findById("loan.stressRate.source")
                .map(banghak.home.halley.domain.setting.SystemConfig::configValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
    }

    private String rateSource(Optional<MarketRate> marketRate, RegulationParams params) {
        return marketRate
                .map(MarketRate::describe)
                .orElseGet(() -> String.format("기본 금리 %s%% 적용 중 (공시 금리를 받지 못했습니다)",
                        params.interestRate().multiply(java.math.BigDecimal.valueOf(100))
                                .stripTrailingZeros().toPlainString()));
    }

 /** 같은 그룹 사용자들의 보유 현금 합계. */
    private long groupCash(Long groupId) {
        if (groupId == null) {
            return 0L;
        }
        return userRepository.findByGroupId(groupId).stream()
                .filter(User::enabled)
                .mapToLong(User::cashOrZero)
                .sum();
    }

    private LoanEstimateResponse estimateMortgage(Long propertyId, Property property, long askingPrice,
                                                  long annualIncome, long cash, long existingLoan,
                                                  long groupCash, LoanEstimateRequest request,
                                                  Map<String, String> rawParams, RegulationParams params,
                                                  Optional<MarketRate> marketRate) {
        final boolean firstHome = Boolean.TRUE.equals(request.firstHome());
        final RateType rateType = request.rateType() == null ? RateType.VARIABLE : request.rateType();
        final boolean insured = Boolean.TRUE.equals(request.mortgageInsured());

        final CollateralValuation collateral = CollateralValuator.estimate(
                property.kbPrice(), tradeSamples(property), property.areaExclusiveM2(),
                property.officialPrice(), askingPrice, params.officialPriceRatio(), LocalDate.now());

        final RegulationZone zone = regulatedAreaService.resolve(property);
        final HouseOwnership ownership = HouseOwnership.of(request.ownedHouseCount());
        final LtvDecision ltv = MortgagePolicy.decide(zone, ownership, firstHome, rawParams, params);

        final LoanEstimateResult result = new LoanCalculator(ltv.rate(), ltv.cap())
                .estimate(new LoanEstimateInput(askingPrice, collateral, annualIncome, cash,
                        existingLoan, myDebts(), firstHome, insured, rateType), withLtv(params, ltv));

        loanEstimateRepository.save(new LoanEstimate(
                null, propertyId, ProductType.MORTGAGE, ltv.rate(),
                result.ltvLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), result.acquisitionTax(),
                mortgageAssumptions(annualIncome, cash, existingLoan, firstHome, insured, result,
                        zone, ownership, ltv),
                Instant.now()));

        return LoanEstimateResponse.mortgage(propertyId, result, askingPrice, annualIncome, cash,
                existingLoan, groupCash, insured, zone, ownership, ltv.rate(), ltv.reason(),
                zoneWarning(), rateSource(marketRate, params), rateType.label(), stressRateSource());
    }

    private LoanEstimateResponse estimateJeonse(Long propertyId, long deposit,
                                                long annualIncome, long cash, long existingLoan,
                                                long groupCash,
                                                Map<String, String> rawParams, RegulationParams params,
                                                Optional<MarketRate> marketRate) {
        final JeonseTerms terms = JeonsePolicy.resolve(rawParams, params);
        final JeonseEstimateResult result = new JeonseLoanCalculator(terms)
                .estimate(new JeonseEstimateInput(deposit, annualIncome, cash, existingLoan), params);

        loanEstimateRepository.save(new LoanEstimate(
                null, propertyId, ProductType.JEONSE, terms.guaranteeRate(),
                result.guaranteeLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), 0L,
                jeonseAssumptions(annualIncome, cash, existingLoan, terms, result),
                Instant.now()));

        return LoanEstimateResponse.jeonse(propertyId, result, deposit, annualIncome, cash, existingLoan,
                groupCash, rateSource(marketRate, params), stressRateSource());
    }

    public List<LoanEstimateHistoryResponse> history(Long propertyId) {
        propertyAccessGuard.require(propertyId);
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
                params.leaseDeduction(), params.officialPriceRatio(), params.stressApplyRatio());
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
                decimal(values, "valuation.officialPriceRatio", defaults.officialPriceRatio()),
                decimal(values, "loan.stressApplyRatio", defaults.stressApplyRatio()));
    }

 /** 담보가치를 매길 재료. 이 매물의 최근 실거래가. 이미 수집해 둔 캐시만 읽고 국토부를 부르지 않는다 */
    private List<TradeSample> tradeSamples(Property property) {
        return complexService.find(property)
                .map(c -> referenceTransactionRepository.findByComplexAndArea(
                        c.id(), property.areaExclusiveM2(), REFERENCE_AREA_TOLERANCE))
                .orElseGet(List::of).stream()
                .filter(t -> t.price() != null && t.price() > 0)
                .map(t -> new TradeSample(t.price(), t.areaM2(), t.contractDate()))
                .toList();
    }

 /** 어떤 전제로 계산했는지 남긴다. 나중에 값이 왜 그랬는지 되짚을 수 있어야 한다. */
    private ObjectNode mortgageAssumptions(long annualIncome, long cash, long existingLoan,
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

    private ObjectNode jeonseAssumptions(long annualIncome, long cash, long existingLoan,
                                         JeonseTerms terms, JeonseEstimateResult result) {
        return objectMapper.createObjectNode()
                .put("annualIncome", annualIncome)
                .put("cash", cash)
                .put("existingLoan", existingLoan)
                .put("guaranteeRate", terms.guaranteeRate().toPlainString())
                .put("guaranteeCap", terms.guaranteeCap())
                .put("interestOnlyDsr", true)
                .put("dsrLimit", result.dsrLimit());
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
