package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LlmRecommendationRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyCommentRepository;
import banghak.home.halley.adapter.outbound.persistence.UserCriterionScoreRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.llm.LlmJobState;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmRecommendation;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.PropertyComment;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import banghak.home.halley.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * LLM 추천도 산출 (설계 I59).
 *
 * <p>매물 정보와 <b>사용자들의 직장 위치</b>를 던져 0~100의 추천도와 이유를 받습니다.
 * 결과는 `llm_recommendation`에 저장되고 `LLM_RECOMMENDATION` 채점 항목의 입력이 됩니다.
 *
 * <p>입력이 그대로면 다시 부르지 않습니다(`prompt_hash`). 매물을 열 때마다 호출하면 비용이
 * 선형으로 늘고, 같은 입력에 같은 답이 나올 것을 다시 사는 셈이기 때문입니다.
 */
@Slf4j
@Service
public class LlmRecommendationService {

    private static final int MAX_TOKENS = 1024;
    private static final int MAX_REASON_LENGTH = 2000;

    private static final String COMFORT_CODE = "COMFORT";

    private static final String SYSTEM_PROMPT = """
            당신은 한국 부동산 매물을 평가하는 조력자입니다.
            주어진 매물 정보, 주변 시설, 구매자들의 직장 위치를 근거로
            이 매물이 이 구매자들에게 얼마나 적합한지 판단하세요.

            반드시 아래 JSON 형식으로만 답하세요. 다른 문장을 덧붙이지 마세요.
            {"score": <0~100 정수>, "reason": "<한국어 두세 문장>"}

            채점 지침
            - score는 이 구매자들에게 이 매물이 얼마나 좋은 선택인지를 0~100으로 나타냅니다.
            - reason에는 점수의 근거를 구체적으로 적으세요. 어떤 정보가 없어서 판단이 제한됐다면 그것도 밝히세요.
            - [주변 시설]의 지하철역과 도보시간은 통근 판단의 핵심 근거입니다. 있으면 반드시 반영하세요.
            - [코멘트]와 [쾌적함]은 구매자들이 실제로 보고 남긴 것입니다. 제원보다 무겁게 다루세요.
            - 주어지지 않은 정보를 지어내지 마세요.
            """;

    private final LlmPort llmPort;
    private final LlmModelService llmModelService;
    private final LlmRecommendationRepository recommendationRepository;
    private final LlmJobCache jobCache;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PoiDataService poiDataService;
    private final UserCriterionScoreRepository userCriterionScoreRepository;
    private final PropertyCommentRepository commentRepository;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public LlmRecommendationService(LlmPort llmPort,
                                    LlmModelService llmModelService,
                                    LlmRecommendationRepository recommendationRepository,
                                    LlmJobCache jobCache,
                                    PropertyRepository propertyRepository,
                                    UserRepository userRepository,
                                    PoiDataService poiDataService,
                                    UserCriterionScoreRepository userCriterionScoreRepository,
                                    PropertyCommentRepository commentRepository,
                                    ScoringService scoringService,
                                    ObjectMapper objectMapper,
                                    @Value("${llm.enabled:true}") boolean enabled) {
        this.llmPort = llmPort;
        this.llmModelService = llmModelService;
        this.recommendationRepository = recommendationRepository;
        this.jobCache = jobCache;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.poiDataService = poiDataService;
        this.userCriterionScoreRepository = userCriterionScoreRepository;
        this.commentRepository = commentRepository;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** 직장 위치가 이만큼 모이면 판단이 충분히 안정된다고 본다 (설계 I60). */
    static final int ENOUGH_WORKPLACES = 3;

    /** 화면이 "지금 분석 중인가"를 물어볼 키 (설계 I72). */
    public static String jobKey(Long propertyId) {
        return "rec:" + propertyId;
    }

    /**
     * 저장된 추천도를 읽는다 — LLM을 부르지 않는다 (설계 I72).
     *
     * <p><b>캐시 우선, DB 폴백</b>입니다. 폴링이 2초마다 두드리므로 캐시가 DB를 막아 줍니다.
     * 다만 캐시가 비었다고 "결과 없음"으로 답하면 <b>DB에 멀쩡히 있는 값을 못 산출로</b>
     * 보여주게 되므로, 미스가 나면 반드시 DB를 보고 캐시를 다시 채웁니다.
     */
    public Optional<LlmRecommendation> find(Long propertyId) {
        final String key = jobKey(propertyId);
        final Optional<LlmRecommendation> fromCache = jobCache.get(key)
                .filter(state -> !state.isRunning())
                .map(LlmJobState::payload)
                .flatMap(this::readCached);
        if (fromCache.isPresent()) {
            return fromCache;
        }
        final Optional<LlmRecommendation> fromDb = recommendationRepository.findByPropertyId(propertyId);
        fromDb.ifPresent(found -> cacheResult(key, found));
        return fromDb;
    }

    private void cacheResult(String key, LlmRecommendation recommendation) {
        try {
            jobCache.markDone(key, objectMapper.writeValueAsString(recommendation));
        } catch (RuntimeException e) {
            // 캐시는 가속기다. 못 담아도 DB에서 읽히므로 기능이 멈추지 않는다
            log.warn("Failed to cache LLM recommendation. key={}, cause={}", key, e.getMessage());
        }
    }

    private Optional<LlmRecommendation> readCached(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, LlmRecommendation.class));
        } catch (RuntimeException e) {
            log.warn("Failed to read cached LLM recommendation - falling back to DB. cause={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 지금 이 매물의 분석이 진행 중인지 (설계 I72).
     * 캐시가 비었으면(TTL 만료·Redis 재시작) 진행 중이 아닌 것으로 본다 — 호출 측이 DB를 본다.
     */
    public boolean isRunning(Long propertyId) {
        return jobCache.get(jobKey(propertyId)).map(LlmJobState::isRunning).orElse(false);
    }

    /**
     * 사용자가 추가되거나 직장 위치가 바뀌었을 때 전 매물의 추천도를 다시 뽑는다 (설계 I60).
     *
     * <p>단, <b>직장 위치 3곳 이상으로 이미 추론한 매물은 건너뜁니다.</b> 그 정도면 통근 판단에
     * 필요한 정보가 다 모인 셈이라, 네 번째 사람이 들어왔다고 매물 전체를 다시 물으면
     * 비용만 늘고 점수는 거의 그대로입니다.
     *
     * @return 실제로 다시 물어본 매물 수
     */
    public int refreshForWorkplaceChange() {
        if (!enabled || !llmPort.isEnabled()) {
            return 0;
        }
        int refreshed = 0;
        int skipped = 0;
        // 직장이 바뀐 사람이 속한 그룹의 매물만 다시 묻는다 (설계 I91).
        // 전 매물을 돌면 남의 그룹까지 LLM을 부르는데, 그쪽 판단은 달라지지 않는다
        for (final Property property : propertiesToRefresh()) {
            final Optional<LlmRecommendation> cached = recommendationRepository.findByPropertyId(property.id());
            if (cached.isPresent() && workplaceCountOf(cached.get()) >= ENOUGH_WORKPLACES) {
                skipped++;
                continue;
            }
            final Optional<LlmRecommendation> before = cached;
            final Optional<LlmRecommendation> after = ensureRecommendation(property.id());
            if (after.isPresent() && (before.isEmpty() || !after.get().equals(before.get()))) {
                refreshed++;
            }
        }
        log.info("Refreshed LLM recommendations after workplace change. refreshed={}, skipped={} (>= {} workplaces)",
                refreshed, skipped, ENOUGH_WORKPLACES);
        return refreshed;
    }

    private List<Property> propertiesToRefresh() {
        return propertyRepository.findAll().stream()
                .filter(p -> p.groupId() != null)
                .toList();
    }

    private int workplaceCountOf(LlmRecommendation recommendation) {
        return recommendation.workplaceCount() == null ? 0 : recommendation.workplaceCount();
    }

    /**
     * 필요하면 LLM을 불러 추천도를 갱신한다. 입력이 그대로면 저장된 값을 그대로 쓴다.
     * 실패해도 예외를 던지지 않는다 — 나머지 채점은 그대로 나와야 한다.
     */
    /**
     * 보정이 시작될 때 미리 켠다 (설계 I109).
     *
     * <p>AI 추천도는 보정 사슬의 <b>맨 끝</b>이라, 실제 호출 전까지 수십 초가 흐릅니다.
     * 그동안 진행 표시가 꺼져 있으면 화면은 "아직 산출되지 않았습니다"를 띄우고
     * <b>폴링도 시작하지 않습니다</b> — 뒤늦게 결과가 나와도 모달을 다시 열기 전엔 안 보입니다.
     * 그래서 표시를 <b>호출 시점이 아니라 보정 시작 시점</b>에 켭니다.
     */
    public void markPending(Long propertyId) {
        jobCache.markRunning(jobKey(propertyId));
    }

    /** 보정이 끝났는데도 결과가 없으면 표시를 끈다. 켜 둔 채 두면 화면이 영영 돈다. */
    public void clearPendingIfUnresolved(Long propertyId) {
        if (recommendationRepository.findByPropertyId(propertyId).isEmpty()) {
            jobCache.clear(jobKey(propertyId));
        }
    }

    public Optional<LlmRecommendation> ensureRecommendation(Long propertyId) {
        if (!enabled || !llmPort.isEnabled()) {
            log.debug("Skipping LLM recommendation - provider not enabled. provider={}", llmPort.provider());
            return recommendationRepository.findByPropertyId(propertyId);
        }
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final Property property = found.get();
        final List<User> buyers = activeBuyers(property);
        final String prompt = buildPrompt(property, buyers, poiDataService.ensureNearby(property),
                comfortScoresOf(propertyId), commentRepository.findByPropertyId(propertyId));
        final String hash = sha256(prompt);
        final int workplaces = (int) buyers.stream()
                .filter(u -> u.workplaceLat() != null && u.workplaceLng() != null)
                .count();

        final Optional<LlmRecommendation> cached = recommendationRepository.findByPropertyId(propertyId);
        if (cached.isPresent() && hash.equals(cached.get().promptHash())) {
            return cached;
        }

        // 화면이 진행 중임을 알 수 있게 표시한다 (설계 I72)
        final String key = jobKey(propertyId);
        jobCache.markRunning(key);
        boolean completed = false;
        try {
            // 요청을 보낸 사실 자체를 남긴다 (설계 I107). 응답이 수십 초 걸려서,
            // 이 줄이 없으면 "안 나온다"가 호출 전인지 응답 대기인지 구분할 수 없다
            // 자리마다 고른 모델을 쓴다 (설계 I267)
            final String model = llmModelService.modelFor(LlmFeature.RECOMMENDATION);
            log.info("Asking LLM for recommendation. propertyId={}, provider={}, model={}, buyers={}, "
                            + "workplaces={}, promptChars={}",
                    propertyId, llmPort.provider(), model, buyers.size(), workplaces, prompt.length());
            // 프롬프트 전문은 debug로. '지하철역 정보가 없다'는 식의 엉뚱한 답이 나왔을 때
            // 실제로 무엇을 보냈는지 봐야 원인을 가릴 수 있다
            log.debug("LLM prompt. propertyId={}\n{}", propertyId, prompt);
            final long askedAt = System.currentTimeMillis();
            final LlmResult result = llmPort.complete(new LlmMessage(SYSTEM_PROMPT, prompt, MAX_TOKENS, model));
            log.info("LLM responded. propertyId={}, present={}, elapsedMs={}",
                    propertyId, result.isPresent(), System.currentTimeMillis() - askedAt);
            if (!result.isPresent()) {
                log.warn("LLM recommendation unavailable. propertyId={}, cause={}",
                        propertyId, result.failureCause());
                return cached;
            }
            final Optional<Verdict> verdict = parse(result.text());
            if (verdict.isEmpty()) {
                log.warn("LLM recommendation could not be parsed. propertyId={}, raw={}",
                        propertyId, result.text());
                return cached;
            }
            final LlmRecommendation saved = recommendationRepository.upsert(new LlmRecommendation(
                    null, propertyId, verdict.get().score(), verdict.get().reason(),
                    result.model(), hash, workplaces, Instant.now()));
            // DB에 먼저 넣은 뒤 캐시에 담는다. 순서가 뒤집히면 DB보다 앞선 값이 보인다
            cacheResult(key, saved);
            completed = true;
            log.info("LLM recommendation stored. propertyId={}, score={}, model={}, workplaces={}",
                    propertyId, saved.score(), saved.model(), workplaces);
            rescore(propertyId);
            return Optional.of(saved);
        } finally {
            // 실패·예외로 빠져나갔으면 RUNNING이 남지 않게 지운다
            if (!completed) {
                jobCache.clear(key);
            }
        }
    }

    /**
     * 프롬프트는 <b>줄 단위로 안정적</b>이어야 한다. 순서가 흔들리면 해시가 달라져 같은 입력에도
     * 다시 호출된다. 그래서 필드를 고정 순서로 쓰고 빈 값은 '정보 없음'으로 명시한다.
     */
    String buildPrompt(Property property, List<User> buyers, List<NearbyFacility> nearby,
                       List<Integer> comfortScores, List<PropertyComment> comments) {
        final StringJoiner sb = new StringJoiner("\n");
        sb.add("[매물 정보]");
        sb.add("단지명: " + text(property.name()));
        sb.add("동/호: " + text(property.dongHo()));
        sb.add("거래유형: " + (property.dealType() == null ? "정보 없음" : property.dealType().name()));
        sb.add("매매가/보증금(원): " + number(property.priceDeposit()));
        sb.add("관리비(원/월): " + number(property.maintenanceFee()));
        // 도로명만 주면 모델이 동 이름을 잘못 추정한다 — 실측에서 '삼성로 212'를 보고
        // 대치동을 '삼성동'이라고 했다. 지번주소가 단지 식별에 더 정확하므로 둘 다 준다 (설계 I71)
        sb.add("지번주소: " + text(property.addressJibun()));
        sb.add("도로명주소: " + text(property.addressRoad()));
        sb.add("공급면적(㎡): " + number(property.areaSupplyM2()));
        sb.add("전용면적(㎡): " + number(property.areaExclusiveM2()));
        sb.add("해당층/총층: " + (property.floorNo() == null ? "정보 없음"
                : property.floorNo() + "/" + number(property.floorTotal())));
        sb.add("방/욕실: " + text(property.roomBath()));
        sb.add("향: " + text(property.direction()));
        sb.add("난방: " + text(property.heatingType()));
        sb.add("사용승인연도: " + number(property.approvalYear()));
        sb.add("세대수: " + number(property.totalHouseholds()));
        sb.add("세대당 주차: " + number(property.parkingPerHousehold()));
        sb.add("배정 초등학교: " + text(property.schoolName())
                + (property.schoolWalkMinutes() == null ? "" : " (도보 " + property.schoolWalkMinutes() + "분)"));
        sb.add("공시가격(원): " + number(property.officialPrice()));
        sb.add("KB시세(원): " + number(property.kbPrice()));
        sb.add("좌표: " + (property.lat() == null || property.lng() == null
                ? "정보 없음" : property.lat() + ", " + property.lng()));

        sb.add("");
        sb.add("[주변 시설] 도보 분은 직선거리 기준 환산치");
        appendNearby(sb, nearby);

        sb.add("");
        sb.add("[구매자들의 직장 위치]");
        if (buyers.isEmpty()) {
            sb.add("정보 없음");
        } else {
            for (final User buyer : buyers) {
                sb.add("- " + buyer.nickname() + ": " + text(buyer.workplaceName())
                        + (buyer.workplaceLat() == null || buyer.workplaceLng() == null
                        ? " (좌표 없음)"
                        : " (" + buyer.workplaceLat() + ", " + buyer.workplaceLng() + ")"));
            }
        }

        // 사람이 직접 남긴 판단 — 이게 바뀌면 추천도를 다시 묻는다 (설계 I78)
        sb.add("");
        sb.add("[구매자들이 직접 매긴 공간의 쾌적함] 1~5점");
        if (comfortScores == null || comfortScores.isEmpty()) {
            sb.add("아직 평가 없음");
        } else {
            sb.add(comfortScores.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "))
                    + " (평균 " + average(comfortScores) + ")");
        }

        sb.add("");
        sb.add("[구매자들이 남긴 코멘트]");
        if (comments == null || comments.isEmpty()) {
            sb.add("없음");
        } else {
            for (final PropertyComment comment : comments) {
                sb.add("- " + text(nicknameOf(comment.userId())) + ": " + text(comment.content()));
            }
        }
        return sb.toString();
    }

    /** 쾌적함은 사용자별로 저장되고 총점에는 평균이 들어간다 (설계 I76). */
    private List<Integer> comfortScoresOf(Long propertyId) {
        return userCriterionScoreRepository.findByPropertyId(propertyId).stream()
                .filter(s -> COMFORT_CODE.equals(s.criterionCode()))
                .map(UserCriterionScore::score)
                .toList();
    }

    private String average(List<Integer> scores) {
        return String.format("%.1f", scores.stream().mapToInt(Integer::intValue).average().orElse(0.0));
    }

    private String nicknameOf(Long userId) {
        return userId == null ? null
                : userRepository.findById(userId).map(User::nickname).orElse(null);
    }


    /**
     * 주변 시설을 <b>가까운 순으로</b> 프롬프트에 싣는다.
     *
     * <p>이게 없어서 모델이 "지하철역 접근성 정보가 없어 통근 시간 산정에 한계가 있다"고 답했다.
     * 앱은 역명과 도보시간을 이미 갖고 있었는데({@code StationScorer}가 채점에 쓴다) 프롬프트에만
     * 빠져 있었다. <b>채점이 쓰는 입력은 모델도 봐야 한다</b> — 같은 매물을 두고 서로 다른 근거로
     * 판단하면 추천 사유와 채점 결과가 어긋난다.
     *
     * <p>전부 넣으면 수백 건이라 카테고리마다 가까운 것만 추린다. 순서는 도보시간 → 이름으로
     * 고정한다 — 프롬프트가 흔들리면 해시가 달라져 같은 입력에도 다시 호출된다.
     */
    private void appendNearby(StringJoiner sb, List<NearbyFacility> nearby) {
        if (nearby == null || nearby.isEmpty()) {
            sb.add("정보 없음");
            return;
        }
        for (final NearbyCategory category : NEARBY_CATEGORIES) {
            final List<NearbyFacility> matched = nearby.stream()
                    .filter(f -> category.code().equals(f.category()))
                    .filter(f -> f.walkMinutes() != null)
                    .sorted(Comparator.comparing(NearbyFacility::walkMinutes)
                            .thenComparing(NearbyFacility::name))
                    .toList();
            if (matched.isEmpty()) {
                sb.add(category.label() + ": 반경 내 없음");
                continue;
            }
            final String top = matched.stream()
                    .limit(category.limit())
                    .map(f -> f.name() + " 도보 " + f.walkMinutes() + "분")
                    .collect(java.util.stream.Collectors.joining(", "));
            sb.add(category.label() + ": " + top
                    + (matched.size() > category.limit() ? " 외 " + (matched.size() - category.limit()) + "곳" : ""));
        }
    }

    /** 역은 통근을 좌우하므로 여러 개, 나머지는 가장 가까운 것만 보여도 판단에 충분하다. */
    private static final List<NearbyCategory> NEARBY_CATEGORIES = List.of(
            new NearbyCategory("STATION", "지하철역", 3),
            new NearbyCategory("EDUCATION", "학교·학원", 2),
            new NearbyCategory("AMENITY", "생활편의", 3),
            new NearbyCategory("GREEN", "공원·녹지", 2));

    private record NearbyCategory(String code, String label, int limit) {
    }


    /**
     * AI 추천도가 <b>새로 생겼을 때만</b> 다시 채점한다 (설계 I84).
     *
     * <p>채점 결과는 `property_score`에 저장해 두는데 AI 추천도는 <b>비동기로 나중에</b>
     * 채워집니다. 등록 직후 채점될 때는 아직 없으므로, 다시 채점하지 않으면 비어 있던 그때의
     * 결과가 그대로 남습니다 — <b>상세 모달에는 AI 추천이 보이는데 채점 모달에는 없는</b>
     * 상태가 됩니다. 두 화면이 다른 곳을 읽기 때문입니다.
     *
     * <p>여기에 두는 이유는 <b>값이 실제로 저장된 자리</b>이기 때문입니다. 보정이 끝나는
     * 지점에 두면 AI 결과가 안 바뀌었을 때도 매번 다시 채점해 POI·통근 조회까지 딸려 갑니다.
     * 입력이 그대로면 프롬프트 해시가 같아 여기까지 오지 않습니다(I59).
     */
    private void rescore(Long propertyId) {
        try {
            scoringService.rescore(propertyId);
        } catch (RuntimeException e) {
            // 채점이 실패해도 방금 받은 추천 자체는 살아 있어야 한다
            log.warn("Rescore after LLM recommendation failed. propertyId={}, cause={}",
                    propertyId, e.toString());
        }
    }

    /**
     * 이 매물을 함께 보는 사람들 (설계 I91).
     *
     * <p><b>같은 그룹의 구성원만</b> 훑습니다. 전 사용자를 넣으면 남의 그룹 사람의
     * <b>직장 주소가 프롬프트로 나가고</b>, 그 사람 기준의 통근까지 판단에 섞입니다.
     *
     * <p>세션이 아니라 매물의 그룹으로 좁힙니다 — 배경 보정에서도 도는데 그때는 로그인
     * 사용자가 없습니다.
     *
     * <p>활성 사용자만, 아이디 순으로 — 순서가 흔들리면 프롬프트 해시가 달라집니다.
     */
    private List<User> activeBuyers(Property property) {
        return userRepository.findByGroupId(property.groupId()).stream()
                .filter(User::enabled)
                .sorted(java.util.Comparator.comparing(User::id))
                .toList();
    }

    /**
     * 모델이 JSON만 내도록 지시했지만 앞뒤에 설명이나 코드펜스를 붙이는 경우가 있어
     * 첫 `{`부터 마지막 `}`까지만 잘라 읽는다.
     */
    Optional<Verdict> parse(String raw) {
        final int start = raw.indexOf('{');
        final int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            final JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            if (node.path("score").isMissingNode()) {
                return Optional.empty();
            }
            final double score = node.path("score").asDouble(-1);
            if (score < 0 || score > 100) {
                return Optional.empty();
            }
            final String reason = node.path("reason").asString("");
            return Optional.of(new Verdict(
                    BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP),
                    reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String sha256(String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value;
    }

    private String number(Object value) {
        return value == null ? "정보 없음" : String.valueOf(value);
    }


    record Verdict(BigDecimal score, String reason) {
    }
}
