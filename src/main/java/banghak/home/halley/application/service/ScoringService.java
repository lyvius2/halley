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
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.cache.EditVersionStore;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.InvalidScoreException;
import banghak.home.halley.domain.loan.LoanCalculator;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.CriterionWeight;
import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoringType;
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
import java.util.Set;
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
 /** 채점·전망이 바뀐 것을 화면에 알리는 판 번호 */
    private final ScoreVersionPublisher scoreVersionPublisher;
    private final RegulationParamRepository regulationParamRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ScoringLock scoringLock;
    private final CachePort cache;
    private final PropertyAccessGuard propertyAccessGuard;
    private final UserGroupRepository userGroupRepository;
    private final ScoringEngine scoringEngine;
    private final List<CriterionScorer> scorers;

    public ScoringService(ApplicationEventPublisher eventPublisher,
                          ScoringLock scoringLock,
                          CachePort cache,
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
                          ScoreVersionPublisher scoreVersionPublisher,
                          RegulationParamRepository regulationParamRepository,
                          SystemConfigRepository systemConfigRepository,
                          ScoringEngine scoringEngine,
                          List<CriterionScorer> scorers) {
        this.eventPublisher = eventPublisher;
        this.scoringLock = scoringLock;
        this.cache = cache;
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
        this.scoreVersionPublisher = scoreVersionPublisher;
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.scoringEngine = scoringEngine;
        this.scorers = scorers;
    }

    public List<ScoredPropertyResponse> list(DealType dealType) {
        return list(dealType, false);
    }


    public List<ScoredPropertyResponse> list(DealType dealType, boolean archived) {
        final List<Property> properties = visibleProperties(dealType, archived);
        final ListBatch batch = loadBatch(properties);
        rankMemo.set(byPriorityRank());
        try {
            return sortedList(properties, batch);
        } finally {
            rankMemo.remove();
        }
    }

    private List<ScoredPropertyResponse> sortedList(List<Property> properties, ListBatch batch) {
        final Collator korean = Collator.getInstance(Locale.KOREAN);
        return properties.stream()
                .map(p -> ensureScored(p, batch))
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

 /** 화면에 보일 매물. */
    List<Property> visibleProperties(DealType dealType, boolean archived) {
        return byGroup(dealType).stream()
                .filter(p -> (p.listingStatus() == ListingStatus.ARCHIVED) == archived)
                .toList();
    }

    private List<Property> byGroup(DealType dealType) {
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

 /** 사람이 매긴 점수를 저장한다. */
    @Transactional
    public ScoredPropertyResponse saveManualScores(Long propertyId, Map<String, BigDecimal> scores) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        if (scores != null) {
            final Map<String, ScoringType> types = criterionRepository.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(Criterion::code, Criterion::scoringType));
            final Map<String, PropertyScore> current = propertyScoreRepository.findByPropertyId(propertyId)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            PropertyScore::criterionCode, s -> s, (a, b) -> a));
            for (final Map.Entry<String, BigDecimal> entry : scores.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (isAutoScored(entry.getKey(), types, current)) {
                    log.info("Ignoring manual score for an already auto-scored criterion. "
                            + "propertyId={}, code={}", propertyId, entry.getKey());
                    continue;
                }
                applyManualScore(propertyId, entry.getKey(), entry.getValue());
            }
        }
        return rescore(propertyId);
    }

 /** 자동으로 이미 값이 나온 AUTO 항목인지. */
    private boolean isAutoScored(String code, Map<String, ScoringType> types,
                                 Map<String, PropertyScore> current) {
        if (types.get(code) != ScoringType.AUTO) {
            return false;
        }
        final PropertyScore score = current.get(code);
        return score != null && score.autoScore() != null;
    }

    private void applyManualScore(Long propertyId, String code, BigDecimal value) {
        if (COMFORT_CODE.equals(code)) {
            final int v = value.intValueExact();
            if (v < 1 || v > 5) {
                throw new InvalidScoreException("쾌적함 점수는 1~5 사이여야 합니다");
            }
            userCriterionScoreRepository.upsert(new UserCriterionScore(
                    propertyId, currentUserId(), COMFORT_CODE, v));
            eventPublisher.publishEvent(PropertyInsightChanged.comfortScore(
                    propertyId, nicknameOf(currentUserId()), v));
        } else {
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidScoreException("채점 점수는 0~100 사이여야 합니다");
            }
            propertyScoreRepository.upsertManualScore(propertyId, code, value);
        }
    }

    private ScoredPropertyResponse ensureScored(Property property, Map<String, BigDecimal> weights) {
        final List<PropertyScore> persisted = propertyScoreRepository.findByPropertyId(property.id());
        if (persisted.isEmpty() && enriching(property.id())) {
            return notYetScored(property.id());
        }
        if (persisted.isEmpty() || isStale(property, persisted)) {
            return rescore(property);
        }
        return buildFromPersisted(property, persisted, criteriaByCode(), weights, null);
    }

 /** 지금 배경에서 보정 중인가. */
    private boolean enriching(Long propertyId) {
        return cache.get(CachePort.ENRICHING, String.valueOf(propertyId)).isPresent();
    }

 /** 목록 한 번에 필요한 것을 미리 모아 둔다. */
    private ListBatch loadBatch(List<Property> properties) {
        final List<Long> ids = properties.stream().map(Property::id).toList();
        final Map<Long, List<PropertyScore>> scores = propertyScoreRepository.findByPropertyIds(ids).stream()
                .collect(Collectors.groupingBy(PropertyScore::propertyId));
        final Map<Long, List<UserCriterionScore>> userScores =
                userCriterionScoreRepository.findByPropertyIds(ids).stream()
                        .collect(Collectors.groupingBy(UserCriterionScore::propertyId));
        final Set<Long> hasLlm = llmRecommendationRepository.findByPropertyIds(ids).stream()
                .map(LlmRecommendation::propertyId)
                .collect(Collectors.toSet());
        final Map<Long, String> nicknames = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::id, User::nickname, (a, b) -> a));
        final boolean admin = propertyAccessGuard.isAdmin();
        final Map<Long, String> groupNames = admin
                ? userGroupRepository.findAll().stream()
                        .collect(Collectors.toMap(UserGroup::id, UserGroup::name, (a, b) -> a))
                : Map.of();
        return new ListBatch(criteriaByCode(), loadWeights(), scores, userScores,
                hasLlm, nicknames, groupNames, admin);
    }

    private ScoredPropertyResponse ensureScored(Property property, ListBatch batch) {
        final List<PropertyScore> persisted = batch.scores().getOrDefault(property.id(), List.of());
        if (persisted.isEmpty() && enriching(property.id())) {
            return notYetScored(property.id());
        }
        if (persisted.isEmpty() || isStale(persisted, batch.hasLlm().contains(property.id()))) {
            return rescore(property);
        }
        return buildFromPersisted(property, persisted, batch.criteria(), batch.weights(), batch);
    }

    private Map<String, Criterion> criteriaByCode() {
        return criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));
    }

 /** 목록 한 번에 쓰는 묶음. */
    private record ListBatch(
            Map<String, Criterion> criteria,
            Map<String, BigDecimal> weights,
            Map<Long, List<PropertyScore>> scores,
            Map<Long, List<UserCriterionScore>> userScores,
            Set<Long> hasLlm,
            Map<Long, String> nicknames,
            Map<Long, String> groupNames,
            boolean admin) {
    }

 /** 저장된 채점이 지금 가진 입력보다 낡았는지. */
 /** 배치용. AI 추천 존재 여부를 이미 알고 있을 때. */
    private boolean isStale(List<PropertyScore> persisted, boolean hasLlm) {
        final boolean scoreMissing = persisted.stream()
                .filter(s -> LLM_CODE.equals(s.criterionCode()))
                .allMatch(s -> s.effectiveScore() == null);
        return scoreMissing && hasLlm;
    }

    private boolean isStale(Property property, List<PropertyScore> persisted) {
        final boolean scoreMissing = persisted.stream()
                .filter(s -> LLM_CODE.equals(s.criterionCode()))
                .allMatch(s -> s.effectiveScore() == null);
        return scoreMissing && llmRecommendationRepository.findByPropertyId(property.id()).isPresent();
    }

 /** 한 매물의 채점을 다시 계산한다. 이미 채점 중이면 하지 않습니다. */
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
        scoreVersionPublisher.bump(property.id());
        return toResponse(property, result, weights);
    }

 /** 저장된 채점으로 응답을 만든다. */
    private ScoredPropertyResponse buildFromPersisted(Property property,
                                                      List<PropertyScore> persisted,
                                                      Map<String, Criterion> criteria,
                                                      Map<String, BigDecimal> weights,
                                                      ListBatch batch) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        final List<CriterionScoreView> views = new ArrayList<>();
        final Comparator<String> order = priorityOrder();
        for (final PropertyScore s : persisted.stream()
                .sorted(Comparator.comparing(PropertyScore::criterionCode, order))
                .toList()) {
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
                    othersAverage(property.id(), s.criterionCode(), batch),
                    othersCount(property.id(), s.criterionCode(), batch),
                    myScore(property.id(), s.criterionCode(), batch)));
        }
        final BigDecimal total = totalWeight > 0.0
                ? BigDecimal.valueOf(weightedSum / totalWeight).setScale(2, RoundingMode.HALF_UP)
                : null;
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property, batch),
                editVersionStore.current(versionKey(property.id())), groupNameFor(property, batch)), total, views,
                scoreVersionPublisher.current(property.id()),
                null);
    }

    private ScoredPropertyResponse toResponse(Property property, PropertyScoringResult result,
                                              Map<String, BigDecimal> weights) {
        final Map<String, Criterion> criteria = criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));
        final Comparator<String> order = priorityOrder();
        final List<CriterionScoreView> views = result.criteria().stream()
                .sorted(Comparator.comparing(CriterionScoreResult::code, order))
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
                        othersAverage(property.id(), c.code(), null),
                        othersCount(property.id(), c.code(), null),
                        myScore(property.id(), c.code(), null)))
                .toList();
        return new ScoredPropertyResponse(PropertyResponse.from(property, nicknameOf(property),
                editVersionStore.current(versionKey(property.id())), groupNameFor(property)), result.totalScore(), views,
                scoreVersionPublisher.current(property.id()),
                null);
    }


 /** 나를 뺀 다른 사용자들의 평균. */
    private BigDecimal othersAverage(Long propertyId, String code, ListBatch batch) {
        final List<Integer> others = othersScores(propertyId, code, batch);
        if (others.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(others.stream().mapToInt(Integer::intValue).average().orElse(0.0))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Integer othersCount(Long propertyId, String code, ListBatch batch) {
        final int count = othersScores(propertyId, code, batch).size();
        return count == 0 ? null : count;
    }

 /** 내가 매긴 점수. */
    private Integer myScore(Long propertyId, String code, ListBatch batch) {
        if (!COMFORT_CODE.equals(code)) {
            return null;
        }
        final Long me = currentUserId();
        if (me == null) {
            return null;
        }
        return userScoresOf(propertyId, batch).stream()
                .filter(s -> code.equals(s.criterionCode()) && me.equals(s.userId()))
                .map(UserCriterionScore::score)
                .findFirst()
                .orElse(null);
    }

    private List<Integer> othersScores(Long propertyId, String code, ListBatch batch) {
        if (!COMFORT_CODE.equals(code)) {
            return List.of();
        }
        final Long me = currentUserId();
        return userScoresOf(propertyId, batch).stream()
                .filter(s -> code.equals(s.criterionCode()))
                .filter(s -> me == null || !me.equals(s.userId()))
                .map(UserCriterionScore::score)
                .toList();
    }

 /** 배치가 있으면 거기서, 없으면(단건 조회) 그때 읽는다. */
    private List<UserCriterionScore> userScoresOf(Long propertyId, ListBatch batch) {
        return batch == null
                ? userCriterionScoreRepository.findByPropertyId(propertyId)
                : batch.userScores().getOrDefault(propertyId, List.of());
    }


 /** 아직 채점하지 않은 매물의 응답. */
    public ScoredPropertyResponse notYetScored(Long propertyId) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return new ScoredPropertyResponse(
                PropertyResponse.from(property, nicknameOf(property),
                        editVersionStore.current(versionKey(property.id())), groupNameFor(property)),
                null, List.of(), scoreVersionPublisher.current(property.id()),
                null);
    }


 /** admin에게만 보이는 그룹 이름. */
    private String groupNameFor(Property property, ListBatch batch) {
        if (batch == null) {
            return groupNameFor(property);
        }
        if (!batch.admin() || property.groupId() == null) {
            return null;
        }
        return batch.groupNames().get(property.groupId());
    }

    private String nicknameOf(Property property, ListBatch batch) {
        if (property.createdByNickname() != null && !property.createdByNickname().isBlank()) {
            return property.createdByNickname();
        }
        if (batch == null) {
            return nicknameOf(property.createdBy());
        }
        return property.createdBy() == null ? null : batch.nicknames().get(property.createdBy());
    }

    private String groupNameFor(Property property) {
        if (!propertyAccessGuard.isAdmin() || property.groupId() == null) {
            return null;
        }
        return userGroupRepository.findById(property.groupId()).map(UserGroup::name).orElse(null);
    }

 /** 매물 카드의 등록자 표시 이름. */
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
        final List<User> allUsers = userRepository.findByGroupId(property.groupId());
        final List<User> activeUsers = allUsers.stream().filter(User::enabled).toList();
        final long cashBudget = activeUsers.stream().mapToLong(User::availableBudget).sum();
        final List<Integer> comfortScores = userCriterionScoreRepository.findByPropertyId(property.id()).stream()
                .filter(s -> COMFORT_CODE.equals(s.criterionCode()))
                .map(UserCriterionScore::score)
                .toList();
        final List<NearbyFacility> nearbyFacilities = poiDataService.ensureNearby(property);
        final Map<Long, Integer> commuteMinutes = commuteDataService.ensureCommuteMinutes(property, allUsers);
        final Optional<LlmRecommendation> llm = llmRecommendationRepository.findByPropertyId(property.id());
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

 /** 규제 파라미터(regulation_param)에서 LTV·상한을 읽어 채점용 LoanCalculator를 구성한다. */
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

 /** 채점 항목을 가중치 순위대로 늘어놓는다. */
 /** 한 요청 안에서 순위표를 한 번만 읽는다. */
    private final ThreadLocal<Comparator<String>> rankMemo = new ThreadLocal<>();

    private Comparator<String> priorityOrder() {
        final Comparator<String> remembered = rankMemo.get();
        return remembered != null ? remembered : byPriorityRank();
    }

    private Comparator<String> byPriorityRank() {
        final Map<String, Integer> ranks = criterionWeightRepository.findAll().stream()
                .filter(w -> w.priorityRank() != null)
                .collect(Collectors.toMap(CriterionWeight::criterionCode, CriterionWeight::priorityRank,
                        (a, b) -> a));
        return Comparator
                .comparingInt((String code) -> ranks.getOrDefault(code, Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());
    }

    private Map<String, BigDecimal> loadWeights() {
        final Map<String, BigDecimal> weights = criterionWeightRepository.findAll().stream()
                .collect(Collectors.toMap(CriterionWeight::criterionCode, CriterionWeight::weight));
        warnAboutUnweightedScorers(weights);
        return weights;
    }

 /** 가중치 없는 채점 항목을 시끄럽게 알린다. */
    private void warnAboutUnweightedScorers(Map<String, BigDecimal> weights) {
        final List<String> unweighted = scorers.stream()
                .map(CriterionScorer::code)
                .filter(code -> weights.get(code) == null
                        || weights.get(code).signum() <= 0)
                .toList();
        if (!unweighted.isEmpty()) {
            log.warn("Scoring criteria have no weight - they contribute nothing to the total. codes={}",
                    unweighted);
        }
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

 /** 매물별 채점 판 번호. 화면이 목록 전체를 받지 않고 바뀐 것만 알아내려고 씁니다. */
    public List<ScoreVersionResponse> scoreVersions(DealType dealType, boolean archived) {
        return visibleProperties(dealType, archived).stream()
                .map(p -> new ScoreVersionResponse(p.id(),
                        scoreVersionPublisher.current(p.id())))
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
