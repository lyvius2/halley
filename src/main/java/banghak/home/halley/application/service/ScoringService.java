package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CriterionScoreView;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoreVersionResponse;
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
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.domain.group.UserGroup;
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
import banghak.home.halley.application.event.PropertyInsightChanged;
import banghak.home.halley.application.port.out.cache.ScoringLock;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class ScoringService {

    private static final String COMFORT_CODE = "COMFORT";
    private static final String LLM_CODE = "LLM_RECOMMENDATION";
    /** 채점 한 번은 보통 수백 ms다. 이만큼 기다려도 안 풀리면 잠금이 죽은 것으로 본다. */
    private static final java.time.Duration LOCK_WAIT = java.time.Duration.ofSeconds(5);
    private static final java.time.Duration LOCK_POLL = java.time.Duration.ofMillis(50);

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
    private final ApplicationEventPublisher eventPublisher;
    private final ScoringLock scoringLock;
    private final PropertyAccessGuard propertyAccessGuard;
    private final UserGroupRepository userGroupRepository;
    private final ScoringEngine scoringEngine;
    private final List<CriterionScorer> scorers;

    public ScoringService(ApplicationEventPublisher eventPublisher,
                          ScoringLock scoringLock,
                          PropertyAccessGuard propertyAccessGuard,
                          UserGroupRepository userGroupRepository,
                          PropertyRepository propertyRepository,
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
        this.eventPublisher = eventPublisher;
        this.scoringLock = scoringLock;
        this.propertyAccessGuard = propertyAccessGuard;
        this.userGroupRepository = userGroupRepository;
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
        // admin은 전부, 회원은 자기 그룹만 (설계 I87)
        final List<Property> properties = visibleProperties(dealType);
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
        return ensureScored(propertyAccessGuard.require(propertyId), loadWeights());
    }

    /**
     * 화면에 보일 매물 (설계 I87).
     *
     * <p>admin은 모든 그룹을 봅니다. 회원은 자기 그룹만 보며, <b>그룹이 없으면 아무것도
     * 보지 않습니다</b> — 그룹 없는 회원은 정상 상태가 아니므로 빈 목록이 맞습니다.
     */
    private List<Property> visibleProperties(DealType dealType) {
        if (propertyAccessGuard.isAdmin()) {
            return dealType == null
                    ? propertyRepository.findAll()
                    : propertyRepository.findByDealType(dealType);
        }
        final Long groupId = propertyAccessGuard.currentGroupId().orElse(null);
        if (groupId == null) {
            return List.of();
        }
        return dealType == null
                ? propertyRepository.findByGroupId(groupId)
                : propertyRepository.findByGroupIdAndDealType(groupId, dealType);
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
            // 쾌적함은 AI 추천의 입력이다. 바뀌면 다시 묻는다 (설계 I78)
            eventPublisher.publishEvent(PropertyInsightChanged.comfortScore(propertyId));
        } else {
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidScoreException("채점 점수는 0~100 사이여야 합니다");
            }
            propertyScoreRepository.upsertManualScore(propertyId, code, value);
        }
    }

    private ScoredPropertyResponse ensureScored(Property property, Map<String, BigDecimal> weights) {
        final List<PropertyScore> persisted = propertyScoreRepository.findByPropertyId(property.id());
        if (persisted.isEmpty() || isStale(property, persisted)) {
            return rescore(property);
        }
        return buildFromPersisted(property, weights);
    }

    /**
     * 저장된 채점이 지금 가진 입력보다 낡았는지 (설계 I84).
     *
     * <p>`property_score`는 계산 결과를 담아 두는 곳인데 <b>입력이 비동기로 채워집니다.</b>
     * 등록 직후 한 번 채점될 때는 AI 추천도가 아직 없고, 보정이 그 값을 채운 뒤 다시 채점하지
     * 않으면 비어 있던 결과가 그대로 남습니다 — 상세 모달은 AI 추천을 보여 주는데 채점 모달에는
     * 없는 상태가 됩니다. 두 화면이 다른 곳을 읽기 때문입니다.
     *
     * <p>보정 흐름은 고쳤지만(`PropertyEnrichmentService`), <b>그 전에 등록된 매물</b>은 이미
     * 낡은 채로 저장돼 있습니다. 여기서 알아채고 스스로 고칩니다 — 사용자가 매물을 다시
     * 수정해야 낫는다면 고친 게 아닙니다.
     */
    private boolean isStale(Property property, List<PropertyScore> persisted) {
        final boolean scoreMissing = persisted.stream()
                .filter(s -> LLM_CODE.equals(s.criterionCode()))
                .allMatch(s -> s.effectiveScore() == null);
        return scoreMissing && llmRecommendationRepository.findByPropertyId(property.id()).isPresent();
    }

    /**
     * 한 매물의 채점을 다시 계산한다. <b>이미 채점 중이면 하지 않습니다</b> (설계 I84).
     *
     * <p>보정 완료·AI 응답 도착·수기 저장이 각각 채점을 부르는데 앞의 둘은 비동기라 겹칠 수
     * 있습니다. 겹치면 같은 매물의 `property_score`를 동시에 쓰고, 계산 도중의 절반짜리 상태를
     * 서로 덮어씁니다.
     *
     * <p><b>건너뛰지 않고 기다립니다.</b> 사용자가 방금 저장한 점수를 반영하러 온 호출일 수 있어,
     * 남이 돌고 있다고 저장된 값을 돌려주면 <b>방금 매긴 점수가 화면에 안 보입니다.</b>
     *
     * <p>기다려도 안 풀리면 <b>그냥 진행합니다.</b> 채점 결과는 항목마다 upsert 하므로 겹쳐도
     * 마지막 값이 남을 뿐이고, 잠금 때문에 채점이 통째로 빠지는 편이 더 나쁩니다.
     */
    private ScoredPropertyResponse rescore(Property property) {
        final boolean locked = acquire(property.id());
        try {
            return doRescore(property);
        } finally {
            if (locked) {
                scoringLock.unlock(property.id());
            }
        }
    }

    private boolean acquire(Long propertyId) {
        final long deadline = System.currentTimeMillis() + LOCK_WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (scoringLock.tryLock(propertyId)) {
                return true;
            }
            try {
                Thread.sleep(LOCK_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("Scoring lock not acquired in time - proceeding anyway. propertyId={}", propertyId);
        return false;
    }

    private ScoredPropertyResponse doRescore(Property property) {
        final ScoringContext ctx = buildContext(property);
        final Map<String, BigDecimal> manualScores = loadManualScores(property.id());
        final Map<String, BigDecimal> weights = loadWeights();
        final PropertyScoringResult result = scoringEngine.score(
                property, ctx, orderedScorers(), weights, manualScores);

        // 항목마다 upsert 한다. 등록 시점 채점과 비동기 보정의 재채점이 겹칠 수 있어
        // 지우고 다시 넣으면 유니크 제약에 걸린다 (설계 I84)
        propertyScoreRepository.replaceAll(property.id(), result.criteria().stream()
                .map(criterion -> new PropertyScore(
                        null,
                        property.id(),
                        criterion.code(),
                        criterion.autoScore(),
                        criterion.manualScore(),
                        criterion.effectiveScore(),
                        sourceOf(criterion),
                        criterion.fallbackReason(),
                        criterion.explanation(),
                        Instant.now()))
                .toList());
        // 화면이 바뀐 것을 알아채는 유일한 신호다 (설계 I85)
        editVersionStore.bump(scoreVersionKey(property.id()));
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
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property),
                editVersionStore.current(versionKey(property.id())), groupNameFor(property)), total, views,
                editVersionStore.current(scoreVersionKey(property.id())));
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
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property),
                editVersionStore.current(versionKey(property.id())), groupNameFor(property)), result.totalScore(), views,
                editVersionStore.current(scoreVersionKey(property.id())));
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


    /**
     * 아직 채점하지 않은 매물의 응답 (설계 I84).
     *
     * <p>등록 직후에는 채점하지 않습니다. 그 시점에는 공시가격·초등학교·POI·AI가 하나도 없어
     * <b>거의 모든 항목이 미산출</b>로 나오는데, 곧 보정이 끝나며 덮어씁니다. 의미 없는 계산을
     * 요청 스레드에서 하고, 비동기 보정과 시간이 겹쳐 잠금까지 다투게 됩니다.
     */
    public ScoredPropertyResponse notYetScored(Long propertyId) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return new ScoredPropertyResponse(
                PropertyResponse.from(property, nicknameOf(property),
                        editVersionStore.current(versionKey(property.id())), groupNameFor(property)),
                null, List.of(), editVersionStore.current(scoreVersionKey(property.id())));
    }


    /**
     * admin에게만 보이는 그룹 이름 (설계 I87 · 규칙 5).
     *
     * <p>회원에게는 <b>null입니다.</b> 자기 그룹 매물만 보므로 이름을 붙여도 전부 같은 값이고,
     * 무엇보다 다른 그룹이 있다는 사실 자체를 알려서는 안 됩니다(규칙 7).
     */
    private String groupNameFor(Property property) {
        if (!propertyAccessGuard.isAdmin() || property.groupId() == null) {
            return null;
        }
        return userGroupRepository.findById(property.groupId()).map(UserGroup::name).orElse(null);
    }

    /**
     * 매물 카드의 등록자 표시 이름 (설계 I53 · I88).
     *
     * <p><b>스냅샷을 먼저 봅니다.</b> 탈퇴하면 users 행이 사라져 조회로는 이름을 알 수 없고,
     * 그때 카드에서 등록자가 통째로 비어 버립니다.
     */
    private String nicknameOf(Property property) {
        if (property.createdByNickname() != null && !property.createdByNickname().isBlank()) {
            return property.createdByNickname();
        }
        return nicknameOf(property.createdBy());
    }

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

    /**
     * 채점 결과의 판 번호 (설계 I85).
     *
     * <p>편집 버전(`property:`)과 <b>키를 나눕니다.</b> 매물 정보를 고치지 않아도 채점은
     * 바뀝니다 — 보정이 끝나거나 AI 응답이 오면 바뀝니다. 한 키에 섞으면 화면이 "무엇이
     * 바뀌었는지" 구분하지 못합니다.
     */
    private String scoreVersionKey(Long id) {
        return "score:" + id;
    }

    /**
     * 매물별 채점 판 번호. 화면이 <b>목록 전체를 받지 않고</b> 바뀐 것만 알아내려고 씁니다.
     */
    public List<ScoreVersionResponse> scoreVersions() {
        return propertyRepository.findAllIds().stream()
                .map(id -> new ScoreVersionResponse(id, editVersionStore.current(scoreVersionKey(id))))
                .toList();
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
