package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CriterionScoreView;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.CriterionRepository;
import banghak.home.halley.adapter.outbound.persistence.CriterionWeightRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyScoreRepository;
import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.adapter.outbound.persistence.UserCriterionScoreRepository;
import banghak.home.halley.adapter.outbound.persistence.ComparativeAnalysisRepository;
import banghak.home.halley.adapter.outbound.persistence.LlmRecommendationRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.llm.ComparativeAnalysis;
import banghak.home.halley.domain.llm.LlmRecommendation;
import banghak.home.halley.application.port.out.cache.EditVersionStore;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.InvalidScoreException;
import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoreSource;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import banghak.home.halley.domain.setting.SystemConfig;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.scoring.criterion.CriterionScorer;
import banghak.home.halley.domain.scoring.criterion.ScoringContext;
import banghak.home.halley.domain.scoring.engine.CriterionScoreResult;
import banghak.home.halley.domain.scoring.engine.PropertyScoringResult;
import banghak.home.halley.domain.scoring.engine.ScoringEngine;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScoringService {

    private static final String COMFORT_CODE = "COMFORT";

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final LlmRecommendationRepository llmRecommendationRepository;
    private final ComparativeAnalysisRepository comparativeAnalysisRepository;
    private final CriterionRepository criterionRepository;
    private final CriterionWeightRepository criterionWeightRepository;
    private final PropertyScoreRepository propertyScoreRepository;
    private final UserCriterionScoreRepository userCriterionScoreRepository;
    private final PoiDataService poiDataService;
    private final CommuteDataService commuteDataService;
    private final EditVersionStore editVersionStore;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ScoringEngine scoringEngine;
    private final List<CriterionScorer> scorers;

    public ScoringService(PropertyRepository propertyRepository,
                          UserRepository userRepository,
                          LlmRecommendationRepository llmRecommendationRepository,
                          ComparativeAnalysisRepository comparativeAnalysisRepository,
                          CriterionRepository criterionRepository,
                          CriterionWeightRepository criterionWeightRepository,
                          PropertyScoreRepository propertyScoreRepository,
                          UserCriterionScoreRepository userCriterionScoreRepository,
                          PoiDataService poiDataService,
                          CommuteDataService commuteDataService,
                          EditVersionStore editVersionStore,
                          RegulationParamRepository regulationParamRepository,
                          SystemConfigRepository systemConfigRepository,
                          ScoringEngine scoringEngine,
                          List<CriterionScorer> scorers) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.llmRecommendationRepository = llmRecommendationRepository;
        this.comparativeAnalysisRepository = comparativeAnalysisRepository;
        this.criterionRepository = criterionRepository;
        this.criterionWeightRepository = criterionWeightRepository;
        this.propertyScoreRepository = propertyScoreRepository;
        this.userCriterionScoreRepository = userCriterionScoreRepository;
        this.poiDataService = poiDataService;
        this.commuteDataService = commuteDataService;
        this.editVersionStore = editVersionStore;
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.scoringEngine = scoringEngine;
        this.scorers = scorers;
    }

    public List<ScoredPropertyResponse> list(DealType dealType) {
        final List<Property> properties = dealType == null
                ? propertyRepository.findAll()
                : propertyRepository.findByDealType(dealType);
        final Map<String, BigDecimal> weights = loadWeights();
        final Collator korean = Collator.getInstance(Locale.KOREAN);
        return properties.stream()
                .map(p -> ensureScored(p, weights))
                .sorted((a, b) -> {
                    final int byScore = compareTotals(b.totalScore(), a.totalScore());
                    if (byScore != 0) {
                        return byScore;
                    }
                    final String an = a.property().name();
                    final String bn = b.property().name();
                    if (an == null && bn == null) {
                        return 0;
                    }
                    if (an == null) {
                        return 1;
                    }
                    if (bn == null) {
                        return -1;
                    }
                    return korean.compare(an, bn);
                })
                .toList();
    }

    public ScoredPropertyResponse getScored(Long propertyId) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return ensureScored(property, loadWeights());
    }

    @Transactional
    public ScoredPropertyResponse rescore(Long propertyId) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return rescore(property);
    }

    @Transactional
    public ScoredPropertyResponse saveManualScores(Long propertyId, Map<String, BigDecimal> scores) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        if (scores != null) {
            for (final Map.Entry<String, BigDecimal> entry : scores.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                applyManualScore(propertyId, entry.getKey(), entry.getValue());
            }
        }
        return rescore(propertyId);
    }

    private void applyManualScore(Long propertyId, String code, BigDecimal value) {
        if (COMFORT_CODE.equals(code)) {
            final int v = value.intValueExact();
            if (v < 1 || v > 5) {
                throw new InvalidScoreException("쾌적함 점수는 1~5 사이여야 합니다");
            }
            userCriterionScoreRepository.upsert(new UserCriterionScore(
                    propertyId, currentUserId(), COMFORT_CODE, v));
        } else {
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidScoreException("채점 점수는 0~100 사이여야 합니다");
            }
            propertyScoreRepository.upsertManualScore(propertyId, code, value);
        }
    }

    private ScoredPropertyResponse ensureScored(Property property, Map<String, BigDecimal> weights) {
        if (propertyScoreRepository.findByPropertyId(property.id()).isEmpty()) {
            return rescore(property);
        }
        return buildFromPersisted(property, weights);
    }

    private ScoredPropertyResponse rescore(Property property) {
        final ScoringContext ctx = buildContext(property);
        final Map<String, BigDecimal> manualScores = loadManualScores(property.id());
        final Map<String, BigDecimal> weights = loadWeights();
        final PropertyScoringResult result = scoringEngine.score(
                property, ctx, orderedScorers(), weights, manualScores);

        propertyScoreRepository.deleteByPropertyId(property.id());
        for (final CriterionScoreResult criterion : result.criteria()) {
            propertyScoreRepository.save(new PropertyScore(
                    null,
                    property.id(),
                    criterion.code(),
                    criterion.autoScore(),
                    criterion.manualScore(),
                    criterion.effectiveScore(),
                    sourceOf(criterion),
                    criterion.fallbackReason(),
                    criterion.explanation(),
                    Instant.now()));
        }
        return toResponse(property, result, weights);
    }

    private ScoredPropertyResponse buildFromPersisted(Property property, Map<String, BigDecimal> weights) {
        final List<PropertyScore> persisted = propertyScoreRepository.findByPropertyId(property.id());
        final Map<String, Criterion> criteria = criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        final List<CriterionScoreView> views = new ArrayList<>();
        for (final PropertyScore s : persisted) {
            final double weight = weightOf(s.criterionCode(), weights);
            if (s.effectiveScore() != null) {
                weightedSum += s.effectiveScore().doubleValue() * weight;
                totalWeight += weight;
            }
            final Criterion criterion = criteria.get(s.criterionCode());
            views.add(new CriterionScoreView(
                    s.criterionCode(),
                    criterion == null ? s.criterionCode() : criterion.name(),
                    criterion == null ? null : criterion.scoringType(),
                    s.autoScore(),
                    s.manualScore(),
                    s.effectiveScore(),
                    s.scoreSource() == null ? null : s.scoreSource().name(),
                    s.fallbackReason(),
                    s.explanation(),
                    othersAverage(property.id(), s.criterionCode()),
                    othersCount(property.id(), s.criterionCode())));
        }
        final BigDecimal total = totalWeight > 0.0
                ? BigDecimal.valueOf(weightedSum / totalWeight).setScale(2, RoundingMode.HALF_UP)
                : null;
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property.createdBy()),
                editVersionStore.current(versionKey(property.id()))), total, views);
    }

    private ScoredPropertyResponse toResponse(Property property, PropertyScoringResult result,
                                              Map<String, BigDecimal> weights) {
        final Map<String, Criterion> criteria = criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));
        final List<CriterionScoreView> views = result.criteria().stream()
                .map(c -> new CriterionScoreView(
                        c.code(),
                        criteria.containsKey(c.code()) ? criteria.get(c.code()).name() : c.code(),
                        criteria.get(c.code()) == null ? null : criteria.get(c.code()).scoringType(),
                        c.autoScore(),
                        c.manualScore(),
                        c.effectiveScore(),
                        sourceOf(c).name(),
                        c.fallbackReason(),
                        c.explanation(),
                        othersAverage(property.id(), c.code()),
                        othersCount(property.id(), c.code())))
                .toList();
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property.createdBy()),
                editVersionStore.current(versionKey(property.id()))), result.totalScore(), views);
    }


    /**
     * 나를 뺀 다른 사용자들의 평균 (설계 I76).
     *
     * <p>`COMFORT`는 사람마다 다르게 매기고 총점에는 <b>평균</b>이 들어간다. 내 점수만 보이면
     * 왜 총점이 그렇게 나왔는지 알 수 없으므로 다른 사람들이 어떻게 봤는지 함께 보여 준다.
     * 나를 빼는 이유는 "다른 사람은 어떻게 봤나"가 질문이기 때문이다 — 내 점수가 섞이면
     * 내가 매긴 값에 끌려간다.
     */
    private BigDecimal othersAverage(Long propertyId, String code) {
        final List<Integer> others = othersScores(propertyId, code);
        if (others.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(others.stream().mapToInt(Integer::intValue).average().orElse(0.0))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Integer othersCount(Long propertyId, String code) {
        final int count = othersScores(propertyId, code).size();
        return count == 0 ? null : count;
    }

    private List<Integer> othersScores(Long propertyId, String code) {
        if (!COMFORT_CODE.equals(code)) {
            return List.of();
        }
        final Long me = currentUserId();
        return userCriterionScoreRepository.findByPropertyId(propertyId).stream()
                .filter(s -> code.equals(s.criterionCode()))
                .filter(s -> me == null || !me.equals(s.userId()))
                .map(UserCriterionScore::score)
                .toList();
    }

    /** 매물 카드의 등록자 표시용 닉네임 (설계 I53). */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::nickname).orElse(null);
    }

    private ScoringContext buildContext(Property property) {
        final List<User> allUsers = userRepository.findAll();
        final List<User> activeUsers = allUsers.stream().filter(User::enabled).toList();
        final long cashBudget = activeUsers.stream().mapToLong(User::availableBudget).sum();
        final List<Integer> comfortScores = userCriterionScoreRepository.findByPropertyId(property.id()).stream()
                .filter(s -> COMFORT_CODE.equals(s.criterionCode()))
                .map(UserCriterionScore::score)
                .toList();
        final List<NearbyFacility> nearbyFacilities = poiDataService.ensureNearby(property);
        // I13: 비활성 사용자도 채점 반영 (I10과 동일) — 통근은 전 사용자 기준
        final Map<Long, Integer> commuteMinutes = commuteDataService.ensureCommuteMinutes(property, allUsers);
        // AI 추천도는 채점 루프 안에서 부르지 않는다 — 저장된 값만 읽는다 (설계 I59)
        final Optional<LlmRecommendation> llm = llmRecommendationRepository.findByPropertyId(property.id());
        // 비교 우위도 마찬가지 — 매물 전체를 한 번에 묻는 무거운 작업이라 저장된 값만 읽는다 (설계 I61)
        final Optional<ComparativeAnalysis> comparative =
                comparativeAnalysisRepository.findByPropertyId(property.id());
        return new ScoringContext(cashBudget, comfortScores, LocalDate.now(), loadLoanCalculator(),
                nearbyFacilities, commuteMinutes,
                llm.map(LlmRecommendation::score).orElse(null),
                llm.map(LlmRecommendation::reason).orElse(null),
                comparative.map(ComparativeAnalysis::score).orElse(null),
                comparative.map(ComparativeAnalysis::reason).orElse(null),
                comparative.map(ComparativeAnalysis::rankNo).orElse(null),
                comparative.map(ComparativeAnalysis::propertyCount).orElse(null));
    }

    @Transactional
    public void rescoreAll() {
        for (final Property property : propertyRepository.findAll()) {
            rescore(property);
        }
    }

    /**
     * 규제 파라미터(regulation_param)에서 LTV·상한을 읽어 채점용 LoanCalculator를 구성한다 (설계 I28).
     */
    private LoanCalculator loadLoanCalculator() {
        final String profile = systemConfigRepository.findById("loan.regulation.profile")
                .map(SystemConfig::configValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse("2025-10-15");
        final Map<String, String> values = regulationParamRepository.findByProfile(profile).stream()
                .collect(Collectors.toMap(RegulationParam::paramKey, RegulationParam::paramValue));
        final BigDecimal ltv = decimal(values, "ltv.rate", new BigDecimal("0.4"));
        final long cap = longValue(values, "ltv.totalCap", 1_000_000_000L);
        return new LoanCalculator(ltv, cap);
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

    private List<CriterionScorer> orderedScorers() {
        return scorers.stream()
                .sorted(Comparator.comparing(CriterionScorer::code))
                .toList();
    }

    private Map<String, BigDecimal> loadWeights() {
        return criterionWeightRepository.findAll().stream()
                .collect(Collectors.toMap(CriterionWeight::criterionCode, CriterionWeight::weight));
    }

    private Map<String, BigDecimal> loadManualScores(Long propertyId) {
        return propertyScoreRepository.findByPropertyId(propertyId).stream()
                .filter(s -> s.manualScore() != null)
                .collect(Collectors.toMap(PropertyScore::criterionCode, PropertyScore::manualScore));
    }

    private double weightOf(String code, Map<String, BigDecimal> weights) {
        final BigDecimal weight = weights.get(code);
        return weight == null ? 0.0 : weight.doubleValue();
    }

    private ScoreSource sourceOf(CriterionScoreResult criterion) {
        if (criterion.manualScore() != null) {
            return ScoreSource.MANUAL;
        }
        if (criterion.autoScore() != null) {
            return ScoreSource.AUTO;
        }
        return ScoreSource.FALLBACK;
    }

    private int compareTotals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private String versionKey(Long id) {
        return "property:" + id;
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
