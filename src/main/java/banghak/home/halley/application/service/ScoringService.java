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
        this.regulationParamRepository = regulationParamRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.scoringEngine = scoringEngine;
        this.scorers = scorers;
    }

    public List<ScoredPropertyResponse> list(DealType dealType) {
        return list(dealType, false);
    }

    /**
     * @param archived 아카이빙한 것만 볼 것인가 (설계 I241). 기본은 <b>아니오</b> —
     *                 치운 매물이 목록에 계속 보이면 치운 의미가 없습니다
     */
    public List<ScoredPropertyResponse> list(DealType dealType, boolean archived) {
        // admin은 전부, 회원은 자기 그룹만 (설계 I87)
        final List<Property> properties = visibleProperties(dealType, archived);
        // 목록에 필요한 것을 한 번에 모은다 (설계 I124). 매물마다 따로 부르면
        // 그 수만큼 왕복이 늘어난다 — 느린 DB에서는 그게 그대로 체감 지연이다
        final ListBatch batch = loadBatch(properties);
        // 순위표는 목록 한 번에 한 번만 읽는다 (설계 I238)
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

    /**
     * 화면에 보일 매물 (설계 I87).
     *
     * <p>admin은 모든 그룹을 봅니다. 회원은 자기 그룹만 보며, <b>그룹이 없으면 아무것도
     * 보지 않습니다</b> — 그룹 없는 회원은 정상 상태가 아니므로 빈 목록이 맞습니다.
     */
    List<Property> visibleProperties(DealType dealType, boolean archived) {
        // 아카이빙 여부는 <b>한 군데</b>에서 가른다 (설계 I241).
        // 저장소의 조회 메서드마다 조건을 붙이면 넷 중 하나를 반드시 빠뜨린다
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

    /**
     * 사람이 매긴 점수를 저장한다.
     *
     * <p><b>이미 자동 채점된 AUTO 항목은 덮어쓰지 않습니다</b> (설계 I111). 화면이 칸을
     * 추정값으로 채워 두기 때문에(I76), 쾌적함 하나만 고치고 저장해도 <b>모든 항목이
     * 그대로 되돌아옵니다.</b> 그것을 전부 저장하면 자동 채점이 통째로 수동으로 굳고
     * 산출 근거(`explanation`)도 사라집니다.
     *
     * <p>화면에서도 그 칸들을 잠그지만, 규칙은 <b>여기</b>가 지킵니다 — 낡은 화면이나
     * 다른 경로로 들어와도 자동 채점이 뭉개지면 안 됩니다.
     */
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

    /**
     * 자동으로 이미 값이 나온 AUTO 항목인지.
     *
     * <p>HYBRID(교육여건·녹색환경)는 사람이 고치라고 만든 것이라 잠그지 않습니다.
     * AUTO라도 <b>산출에 실패해 값이 없으면</b> 사람이 채울 수 있어야 합니다 — 그렇지 않으면
     * 조회 한 번 실패한 항목이 영영 빈칸으로 남습니다.
     */
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
            // 쾌적함은 AI 추천의 입력이다. 바뀌면 다시 묻는다 (설계 I78)
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

    /**
     * 지금 배경에서 보정 중인가 (설계 I220).
     *
     * <p>등록 응답을 기다리지 않고 돌려주므로, 목록·상세가 <b>아직 점수가 없는 매물</b>을
     * 만납니다. 그때 그 자리에서 채점하면 <b>기다림이 옮겨 갔을 뿐</b>입니다 —
     * 목록 한 번이 수십 초가 됩니다.
     *
     * <p><b>표시가 있을 때만</b> 비켜섭니다. 없으면 평소대로 계산합니다 —
     * 옛 매물이 어떤 이유로 점수를 잃었을 때 스스로 낫는 길(I84)을 막지 않습니다.
     */
    private boolean enriching(Long propertyId) {
        return cache.get(CachePort.ENRICHING, String.valueOf(propertyId)).isPresent();
    }

    /**
     * 목록 한 번에 필요한 것을 미리 모아 둔다 (설계 I124).
     *
     * <p>예전에는 매물마다 채점·사용자 점수·AI 추천·항목·닉네임을 따로 읽어
     * <b>매물 3건에 28번</b>이 나갔습니다. 여기서 <b>여섯 번</b>으로 끝냅니다.
     *
     * <p>가상 스레드로 나눠 던지는 방법도 있지만 그쪽은 쓰지 않습니다 — 커넥션 풀이
     * 기본 10이고 운영 DB는 이미 슬롯이 빠듯합니다. <b>동시에 던지면 왕복 횟수는 그대로인 채
     * 커넥션만 더 씁니다.</b> 횟수를 줄이는 편이 언제나 낫습니다.
     */
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
        // 닉네임은 매물에 이미 박혀 있는 경우가 많지만, 없는 것 때문에 매물마다 조회가 나갔다
        final Map<Long, String> nicknames = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::id, User::nickname, (a, b) -> a));
        // 그룹명은 admin에게만 나간다 (규칙 5)
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
        // 보정 중이면 비켜선다 (설계 I220) — 목록에서 채점하면 목록이 그만큼 멈춘다
        if (persisted.isEmpty() && enriching(property.id())) {
            return notYetScored(property.id());
        }
        if (persisted.isEmpty() || isStale(persisted, batch.hasLlm().contains(property.id()))) {
            // 낡았으면 그 매물만 다시 계산한다. 흔한 길이 아니다
            return rescore(property);
        }
        return buildFromPersisted(property, persisted, batch.criteria(), batch.weights(), batch);
    }

    private Map<String, Criterion> criteriaByCode() {
        return criterionRepository.findAll().stream()
                .collect(Collectors.toMap(Criterion::code, c -> c));
    }

    /** 목록 한 번에 쓰는 묶음 (설계 I124). */
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
    /** 배치용 — AI 추천 존재 여부를 이미 알고 있을 때 (설계 I124). */
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

    /**
     * 저장된 채점으로 응답을 만든다.
     *
     * @param batch 목록에서 부를 때는 미리 모아 둔 묶음, 단건이면 {@code null} (설계 I124)
     */
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
                editVersionStore.current(scoreVersionKey(property.id())),
                // 전망은 채점의 관심사가 아니다 — 컨트롤러가 붙인다 (설계 I136)
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
                editVersionStore.current(scoreVersionKey(property.id())),
                // 전망은 채점의 관심사가 아니다 — 컨트롤러가 붙인다 (설계 I136)
                null);
    }


    /**
     * 나를 뺀 다른 사용자들의 평균 (설계 I76).
     *
     * <p>`COMFORT`는 사람마다 다르게 매기고 총점에는 <b>평균</b>이 들어간다. 내 점수만 보이면
     * 왜 총점이 그렇게 나왔는지 알 수 없으므로 다른 사람들이 어떻게 봤는지 함께 보여 준다.
     * 나를 빼는 이유는 "다른 사람은 어떻게 봤나"가 질문이기 때문이다 — 내 점수가 섞이면
     * 내가 매긴 값에 끌려간다.
     */
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

    /**
     * 내가 매긴 점수 (설계 I118).
     *
     * <p>`COMFORT`는 사람마다 따로 매기는데 응답에는 <b>그룹 평균</b>만 실려 있었습니다.
     * 그래서 남이 매기면 화면이 그 값을 보여 주고, 나는 <b>내가 이미 매긴 줄</b> 알았습니다.
     * 내 값과 그룹 값은 다른 것이므로 따로 실어 보냅니다.
     */
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

    /** 배치가 있으면 거기서, 없으면(단건 조회) 그때 읽는다 (설계 I124). */
    private List<UserCriterionScore> userScoresOf(Long propertyId, ListBatch batch) {
        return batch == null
                ? userCriterionScoreRepository.findByPropertyId(propertyId)
                : batch.userScores().getOrDefault(propertyId, List.of());
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
                null, List.of(), editVersionStore.current(scoreVersionKey(property.id())),
                // 전망은 채점의 관심사가 아니다 — 컨트롤러가 붙인다 (설계 I136)
                null);
    }


    /**
     * admin에게만 보이는 그룹 이름 (설계 I87 · 규칙 5).
     *
     * <p>회원에게는 <b>null입니다.</b> 자기 그룹 매물만 보므로 이름을 붙여도 전부 같은 값이고,
     * 무엇보다 다른 그룹이 있다는 사실 자체를 알려서는 안 됩니다(규칙 7).
     */
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
        // 그룹 구성원만 본다 (설계 I91). 세션이 아니라 <b>매물의 그룹</b>으로 좁힌다 —
        // 배경 보정에서도 도는데 그때는 로그인 사용자가 없다
        final List<User> allUsers = userRepository.findByGroupId(property.groupId());
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

    /**
     * 채점 항목을 <b>가중치 순위대로</b> 늘어놓는다 (설계 I199).
     *
     * <p>같은 매물의 채점을 두 곳에서 만들고 있었습니다 — 읽을 때는 `buildFromPersisted`
     * 가 <b>DB 가 준 순서</b>대로, 저장 뒤 재채점할 때는 `toResponse` 가 <b>채점기 등록
     * 순서</b>대로. 그래서 <b>저장을 누르면 항목이 뒤섞였습니다.</b>
     *
     * <p>둘 다 이 비교자를 지나가게 해 순서를 하나로 맞춥니다. 그리고 그 순서는
     * <b>가중치 순위</b>입니다 — 총점에 크게 물리는 것이 위에 옵니다. 순위가 없는 항목은
     * 뒤로 보내되 코드순으로 묶어, 무게가 0인 것들끼리도 자리가 흔들리지 않게 합니다.
     */
    /**
     * 한 요청 안에서 순위표를 <b>한 번만</b> 읽는다 (설계 I238).
     *
     * <p>[I199]에서 정렬을 넣으며 `byPriorityRank()` 를 <b>매물마다</b> 불렀습니다 —
     * 실측하니 매물 6건에 `criterion_weight` 조회가 <b>7회</b>였습니다.
     * [I124]에서 걷어낸 N+1을 제가 다시 만든 것입니다.
     *
     * <p>14행짜리 표라 눈에 안 띄지만, 매물이 늘면 그대로 늘어납니다.
     * <b>캐시를 얹기 전에 이것부터</b>입니다 — 캐시는 증상을 가릴 뿐입니다.
     */
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

    /**
     * 가중치 없는 채점 항목을 <b>시끄럽게</b> 알린다 (설계 I152).
     *
     * <p>가중치가 없으면 총점에서 그 항목의 무게가 0이 됩니다 — 점수는 화면에 멀쩡히 뜨는데
     * <b>총점만 꿈쩍하지 않습니다.</b> 조용히 틀리는 쪽이라 눈으로는 못 찾습니다.
     *
     * <p>기동 때 `CriteriaBootstrap`이 메우지만, 그 뒤에 항목이 생기거나 가중치가
     * 지워지는 일도 있습니다. 채점할 때마다 확인합니다.
     */
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
     *
     * <p><b>목록과 같은 것을 세야 합니다</b> (설계 I241). `findAllIds()` 는 그룹도
     * 아카이빙도 안 보고 <b>모든 매물</b>을 돌려주고 있었습니다. 화면은 이 개수를
     * 자기 목록 길이와 견주어 "매물이 늘거나 줄었다"를 판단하므로, 세는 대상이
     * 다르면 <b>3초마다 목록을 통째로 다시 받습니다.</b>
     *
     * <p>목록이 30건씩 잘려 오기 시작하면서([I240]) 이 어긋남이 <b>드러났습니다</b> —
     * 전에도 거래유형 탭에서는 같은 일이 벌어지고 있었습니다.
     */
    public List<ScoreVersionResponse> scoreVersions(DealType dealType, boolean archived) {
        return visibleProperties(dealType, archived).stream()
                .map(p -> new ScoreVersionResponse(p.id(),
                        editVersionStore.current(scoreVersionKey(p.id()))))
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
