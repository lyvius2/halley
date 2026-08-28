package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
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
    private final UserRepository userRepository;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final LoanEstimateRepository loanEstimateRepository;
    private final ObjectMapper objectMapper;

    public LoanEstimateService(PropertyRepository propertyRepository,
                               UserRepository userRepository,
                               RegulationParamRepository regulationParamRepository,
                               SystemConfigRepository systemConfigRepository,
                               LoanEstimateRepository loanEstimateRepository,
                               ObjectMapper objectMapper) {
        this.propertyRepository = propertyRepository;
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
        final RegulationParams params = loadParams();

        // 입력이 비면 로그인 사용자의 프로필로 채운다 — 모달을 열자마자 결과가 보여야 한다 (설계 I55)
        final Optional<User> me = currentUser();
        final long annualIncome = orProfile(request.annualIncome(), me.map(User::annualIncomeOrZero));
        final long cash = orProfile(request.cash(), me.map(User::cashOrZero));
        final long existingLoan = orProfile(request.existingLoan(), me.map(User::existingLoanOrZero));
        final boolean firstHome = Boolean.TRUE.equals(request.firstHome());

        final LoanEstimateResult result = new LoanCalculator(params.ltvRate(), params.totalCap())
                .estimate(askingPrice, annualIncome, cash, existingLoan, firstHome, params);

        loanEstimateRepository.save(new LoanEstimate(
                null, propertyId, ProductType.MORTGAGE, params.ltvRate(),
                result.ltvLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), result.acquisitionTax(),
                assumptions(annualIncome, cash, existingLoan, firstHome),
                Instant.now()));

        return new LoanEstimateResponse(
                propertyId, result.ltvLimit(), result.dsrLimit(), result.finalLimit(),
                result.requiredCash(), result.acquisitionTax(), result.monthlyPayment(),
                askingPrice, annualIncome, cash, existingLoan,
                result.dsrCapacity(), result.existingLoanAnnual(),
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

    private RegulationParams loadParams() {
        final String profile = systemConfigRepository.findById(PROFILE_KEY)
                .map(SystemConfig::configValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(DEFAULT_PROFILE);
        final Map<String, String> values = regulationParamRepository.findByProfile(profile).stream()
                .collect(Collectors.toMap(RegulationParam::paramKey, RegulationParam::paramValue));

        final RegulationParams defaults = RegulationParams.defaults();
        return new RegulationParams(
                decimal(values, "ltv.rate", defaults.ltvRate()),
                longValue(values, "ltv.totalCap", defaults.totalCap()),
                decimal(values, "dsr.ratio", defaults.dsrRatio()),
                decimal(values, "loan.interestRate", defaults.interestRate()),
                decimal(values, "loan.stressRate", defaults.stressRate()),
                intValue(values, "loan.termYears", defaults.termYears()),
                decimal(values, "tax.acquisitionRate", defaults.acquisitionTaxRate()),
                decimal(values, "tax.firstHomeDiscount", defaults.firstHomeDiscount()));
    }

    private ObjectNode assumptions(long annualIncome, long cash, long existingLoan, boolean firstHome) {
        return objectMapper.createObjectNode()
                .put("annualIncome", annualIncome)
                .put("cash", cash)
                .put("existingLoan", existingLoan)
                .put("firstHome", firstHome);
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
