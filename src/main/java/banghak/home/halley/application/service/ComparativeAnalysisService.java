package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ComparativeAnalysisResponse;
import banghak.home.halley.adapter.inbound.web.dto.ComparativeAnalysisStatus;
import banghak.home.halley.adapter.outbound.persistence.ComparativeAnalysisRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.llm.LlmJobState;
import banghak.home.halley.config.exception.InsufficientPropertiesException;
import banghak.home.halley.config.exception.LlmUnavailableException;
import banghak.home.halley.domain.llm.ComparativeAnalysis;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * 비교 우위 분석 (설계 I61).
 *
 * <p>개별 매물을 따로 보는 AI 추천도(I59)와 달리, <b>등록된 매물 전체를 한 번에</b> LLM에 던져
 * 서로 견주게 하고 순위와 `비교 우위 추천` 점수를 받습니다. 같은 정보를 봐도 "이 집이 괜찮은가"와
 * "이 집이 저 집보다 나은가"는 다른 질문이라 따로 둡니다.
 *
 * <p><b>매물이 4개 미만이면 실행하지 않습니다.</b> 둘셋으로는 비교 우위라는 말이 성립하지 않고,
 * 순위를 매겨도 정보가 거의 없습니다.
 */
@Slf4j
@Service
public class ComparativeAnalysisService {

    /** 이보다 적으면 순위가 뜻을 갖지 못한다 (설계 I61). */
    public static final int MIN_PROPERTIES = 4;

    private static final int MAX_TOKENS = 4096;
    private static final int MAX_REASON_LENGTH = 2000;

    private static final String SYSTEM_PROMPT = """
            당신은 한국 부동산 매물을 비교 평가하는 조력자입니다.
            여러 매물과 구매자들의 직장 위치가 주어집니다. 이 매물들을 서로 견주어 순위를 매기세요.

            반드시 아래 JSON 형식으로만 답하세요. 다른 문장을 덧붙이지 마세요.
            {"rankings": [{"propertyId": <정수>, "rank": <1부터>, "score": <0~100 정수>, "reason": "<한국어 두세 문장>"}]}

            채점 지침
            - 주어진 모든 매물을 빠짐없이 포함하세요. rank는 1부터 시작해 중복 없이 매기세요.
            - score는 다른 매물들과 견준 상대적 우위입니다. 1위가 가장 높아야 합니다.
            - reason에는 "다른 매물 대비" 무엇이 낫고 무엇이 못한지를 구체적으로 적으세요.
            - 주어지지 않은 정보를 지어내지 마세요.
            """;

    private final LlmPort llmPort;
    private final LlmModelService llmModelService;
    private final ComparativeAnalysisRepository analysisRepository;
    private final LlmJobCache jobCache;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PropertyAccessGuard accessGuard;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ComparativeAnalysisService(LlmPort llmPort,
                                    LlmModelService llmModelService,
                                      ComparativeAnalysisRepository analysisRepository,
                                      LlmJobCache jobCache,
                                      PropertyRepository propertyRepository,
                                      UserRepository userRepository,
                                      PropertyAccessGuard accessGuard,
                                      ScoringService scoringService,
                                      ObjectMapper objectMapper,
                                      @Value("${llm.enabled:true}") boolean enabled) {
        this.llmPort = llmPort;
        this.llmModelService = llmModelService;
        this.analysisRepository = analysisRepository;
        this.jobCache = jobCache;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** 비교 우위는 매물 단위가 아니라 전체 단위라 키가 하나다 (설계 I72). */
    public static final String JOB_KEY = "compare";

    /** 지금 분석이 진행 중인지. */
    public boolean isRunning() {
        return jobCache.get(JOB_KEY).map(LlmJobState::isRunning).orElse(false);
    }

    /** 저장된 분석 결과만 읽는다 — LLM을 부르지 않는다. 순위 오름차순. */
    public List<ComparativeAnalysis> findAll() {
        return analysisRepository.findAll();
    }

    public Optional<ComparativeAnalysis> find(Long propertyId) {
        return analysisRepository.findByPropertyId(propertyId);
    }

    /**
     * 현황 — 지금 실행할 수 있는지와 저장된 순위. 화면이 버튼을 열지 말지 이 값으로 판단한다.
     */
    public ComparativeAnalysisStatus status() {
        final List<Property> targets = targets();
        final Map<Long, String> names = targets.stream()
                .collect(Collectors.toMap(Property::id, p -> p.name() == null ? "" : p.name()));
        final List<ComparativeAnalysisResponse> rankings = analysisRepository.findAll().stream()
                .map(a -> ComparativeAnalysisResponse.from(a, names.getOrDefault(a.propertyId(), "")))
                .toList();
        return new ComparativeAnalysisStatus(
                isRunning(),
                targets.size() >= MIN_PROPERTIES && enabled && llmPort.isEnabled(),
                targets.size(), MIN_PROPERTIES, rankings);
    }

    /**
     * 등록된 매물 전체를 견주어 순위와 점수를 매긴다.
     *
     * <p>매물 집합이 그대로면 다시 부르지 않습니다(`batch_hash`). 매물이 추가·수정되면
     * 해시가 달라져 다시 분석합니다.
     */
    @Transactional
    public List<ComparativeAnalysis> analyse() {
        final List<Property> targets = targets();
        if (targets.size() < MIN_PROPERTIES) {
            throw new InsufficientPropertiesException(MIN_PROPERTIES, targets.size());
        }
        if (!enabled || !llmPort.isEnabled()) {
            throw new LlmUnavailableException();
        }
        final List<User> buyers = activeBuyers();
        final String prompt = buildPrompt(targets, buyers);
        final String hash = sha256(prompt);

        final List<ComparativeAnalysis> cached = analysisRepository.findAll();
        if (isFresh(cached, hash, targets)) {
            return cached;
        }

        // 매물 전체를 한 번에 묻느라 오래 걸린다. 화면이 진행 중임을 알 수 있게 표시한다 (설계 I72)
        jobCache.markRunning(JOB_KEY);
        final LlmResult result;
        final List<Ranking> rankings;
        try {
            // 자리마다 고른 모델을 쓴다 (설계 I267)
            final String model = llmModelService.modelFor(LlmFeature.COMPARATIVE);
            log.info("Asking LLM for comparative analysis. model={}, targets={}, promptChars={}",
                    model, targets.size(), prompt.length());
            result = llmPort.complete(new LlmMessage(SYSTEM_PROMPT, prompt, MAX_TOKENS, model));
            if (!result.isPresent()) {
                log.warn("Comparative analysis unavailable. cause={}", result.failureCause());
                throw new LlmUnavailableException();
            }
            rankings = parse(result.text(), targets);
            if (rankings.isEmpty()) {
                log.warn("Comparative analysis could not be parsed. raw={}", result.text());
                throw new LlmUnavailableException();
            }
        } finally {
            // 결과는 DB에서 읽는다(매물마다 한 행이라 캐시 한 칸에 담기 부적절).
            // 여기서는 '진행 중' 표시만 걷어낸다
            jobCache.clear(JOB_KEY);
        }

        final Instant now = Instant.now();
        final List<ComparativeAnalysis> saved = new ArrayList<>();
        for (final Ranking ranking : rankings) {
            saved.add(analysisRepository.upsert(new ComparativeAnalysis(
                    null, ranking.propertyId(), ranking.rank(), ranking.score(), ranking.reason(),
                    result.model(), hash, targets.size(), now)));
        }
        // 이번 분석에 없던 매물의 옛 결과는 지운다 — 몇 개 중의 몇 위인지가 어긋나면 순위가 거짓이 된다
        final Set<Long> analysed = rankings.stream().map(Ranking::propertyId).collect(Collectors.toSet());
        for (final ComparativeAnalysis stale : cached) {
            if (!analysed.contains(stale.propertyId())) {
                analysisRepository.deleteByPropertyId(stale.propertyId());
            }
        }
        log.info("Comparative analysis stored. properties={}, model={}", saved.size(), result.model());
        scoringService.rescoreAll();
        return saved.stream()
                .sorted(Comparator.comparing(ComparativeAnalysis::rankNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 비교 대상 매물 (설계 I91).
     *
     * <p><b>내 그룹 매물만 견줍니다.</b> 전 매물을 한 줄로 세우면 남의 그룹 매물이 순위에
     * 섞이고, 무엇보다 그 매물 정보가 <b>LLM 프롬프트로 나갑니다.</b>
     *
     * <p>판매완료·초안은 제외합니다 — 살 수 없는 집과 견주면 순위가 왜곡됩니다.
     */
    private List<Property> targets() {
        final Long groupId = accessGuard.currentGroupId().orElse(null);
        if (groupId == null) {
            // admin은 그룹이 없다. 어느 그룹의 순위를 매길지 정해지지 않으므로 대상이 없다
            return List.of();
        }
        return propertyRepository.findByGroupId(groupId).stream()
                .filter(Property::active)
                .filter(p -> !p.isDraft())
                .sorted(Comparator.comparing(Property::id))
                .toList();
    }

    /** 저장된 결과가 이번 매물 집합과 해시·구성원까지 같은지. */
    private boolean isFresh(List<ComparativeAnalysis> cached, String hash, List<Property> targets) {
        if (cached.size() != targets.size()) {
            return false;
        }
        final Set<Long> cachedIds = cached.stream()
                .map(ComparativeAnalysis::propertyId)
                .collect(Collectors.toCollection(HashSet::new));
        final Set<Long> targetIds = targets.stream().map(Property::id).collect(Collectors.toSet());
        return cachedIds.equals(targetIds)
                && cached.stream().allMatch(c -> hash.equals(c.batchHash()));
    }

    /**
     * 프롬프트는 <b>줄 순서가 안정적</b>이어야 한다. 흔들리면 해시가 달라져 같은 입력에도 다시 호출된다.
     * 매물은 id 순, 필드는 고정 순서로 쓰고 빈 값은 '정보 없음'으로 명시한다.
     */
    String buildPrompt(List<Property> properties, List<User> buyers) {
        final StringJoiner sb = new StringJoiner("\n");
        sb.add("[비교 대상 매물 " + properties.size() + "건]");
        for (final Property p : properties) {
            sb.add("");
            sb.add("## propertyId=" + p.id());
            sb.add("단지명: " + text(p.name()));
            sb.add("동/호: " + text(p.dongHo()));
            sb.add("거래유형: " + (p.dealType() == null ? "정보 없음" : p.dealType().name()));
            sb.add("매매가/보증금(원): " + number(p.priceDeposit()));
            sb.add("관리비(원/월): " + number(p.maintenanceFee()));
            // 도로명만 주면 모델이 동 이름을 잘못 추정한다 (설계 I71)
            sb.add("지번주소: " + text(p.addressJibun()));
            sb.add("도로명주소: " + text(p.addressRoad()));
            sb.add("공급면적(㎡): " + number(p.areaSupplyM2()));
            sb.add("전용면적(㎡): " + number(p.areaExclusiveM2()));
            sb.add("해당층/총층: " + (p.floorNo() == null ? "정보 없음"
                    : p.floorNo() + "/" + number(p.floorTotal())));
            sb.add("방/욕실: " + text(p.roomBath()));
            sb.add("향: " + text(p.direction()));
            sb.add("난방: " + text(p.heatingType()));
            sb.add("사용승인연도: " + number(p.approvalYear()));
            sb.add("세대수: " + number(p.totalHouseholds()));
            sb.add("세대당 주차: " + number(p.parkingPerHousehold()));
            sb.add("배정 초등학교: " + text(p.schoolName())
                    + (p.schoolWalkMinutes() == null ? "" : " (도보 " + p.schoolWalkMinutes() + "분)"));
            sb.add("공시가격(원): " + number(p.officialPrice()));
            sb.add("KB시세(원): " + number(p.kbPrice()));
            sb.add("입주: " + (p.moveInType() == null ? "정보 없음" : p.moveInType().name())
                    + (p.moveInDate() == null ? "" : " " + p.moveInDate()));
            sb.add("좌표: " + (p.lat() == null || p.lng() == null
                    ? "정보 없음" : p.lat() + ", " + p.lng()));
        }
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
        return sb.toString();
    }

    /**
     * 이 매물들을 함께 보는 사람들 (설계 I91).
     *
     * <p><b>같은 그룹의 구성원만</b> 훑습니다. 전 사용자를 넣으면 남의 그룹 사람의
     * 직장 주소가 프롬프트로 나갑니다.
     */
    private List<User> activeBuyers() {
        return accessGuard.currentGroupId()
                .map(userRepository::findByGroupId)
                .orElseGet(List::of).stream()
                .filter(User::enabled)
                .sorted(Comparator.comparing(User::id))
                .toList();
    }

    /**
     * 모델이 코드펜스나 설명을 덧붙이는 경우가 있어 첫 `{`부터 마지막 `}`까지만 잘라 읽는다.
     * 대상에 없는 propertyId나 범위를 벗어난 점수는 버리고, 하나라도 빠지면 결과 전체를 버린다 —
     * 일부만 순위가 매겨지면 "몇 개 중 몇 위"가 거짓이 된다.
     */
    List<Ranking> parse(String raw, List<Property> targets) {
        final int start = raw.indexOf('{');
        final int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        final Map<Long, Property> byId = targets.stream()
                .collect(Collectors.toMap(Property::id, p -> p));
        try {
            final JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
            final List<Ranking> rankings = new ArrayList<>();
            final Set<Long> seen = new HashSet<>();
            for (final JsonNode node : root.path("rankings")) {
                final long propertyId = node.path("propertyId").asLong(-1);
                if (!byId.containsKey(propertyId) || !seen.add(propertyId)) {
                    continue;
                }
                final double score = node.path("score").asDouble(-1);
                final int rank = node.path("rank").asInt(-1);
                if (score < 0 || score > 100 || rank < 1) {
                    continue;
                }
                final String reason = node.path("reason").asString("");
                rankings.add(new Ranking(
                        propertyId, rank,
                        BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP),
                        reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason));
            }
            if (rankings.size() != targets.size()) {
                log.warn("Comparative analysis covered {} of {} properties - discarding.",
                        rankings.size(), targets.size());
                return List.of();
            }
            return rankings.stream()
                    .sorted(Comparator.comparingInt(Ranking::rank))
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
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


    record Ranking(Long propertyId, Integer rank, BigDecimal score, String reason) {
    }
}
