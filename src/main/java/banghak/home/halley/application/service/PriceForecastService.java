package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LandUseRepository;
import banghak.home.halley.adapter.outbound.persistence.PriceForecastRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.application.port.out.external.LoanRateHistoryPort;
import banghak.home.halley.domain.building.BuildingLedger;
import banghak.home.halley.application.port.out.external.BuildingLedgerPort;
import banghak.home.halley.adapter.inbound.web.dto.ForecastSummary;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.domain.forecast.PriceForecast;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.ForecastPrompt;
import banghak.home.halley.domain.forecast.ForecastVerdictParser;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceOutlook;
import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 지표를 놓고 LLM에게 방향을 묻는다 (설계 I134).
 *
 * <p>흐름은 이렇습니다.
 *
 * <pre>
 *   지표 계산 (코드)
 *         ├──→ 코드 예측   ← LLM에게 넘기지 않는다 (앵커링 차단, 4.5)
 *         └──→ LLM 판단    ← 결론
 * </pre>
 *
 * <p><b>방향은 LLM이 정하지만, 판단이 아니라 사실의 문제인 것은 코드가 강제합니다</b>(2.2-A).
 */
@Slf4j
@Service
public class PriceForecastService {

    /**
     * 예산이 모자라면 <b>답이 JSON 중간에서 잘립니다</b> (설계 I149).
     *
     * <p>1,500으로 뒀다가 운영에서 정확히 다 쓰고 잘렸습니다. 요인이 여섯으로 늘어(I148)
     * 답이 길어진 데다, <b>요즘 모델은 생각(thinking)에도 예산을 씁니다</b> —
     * 그날 549토큰이 생각에 나갔습니다.
     *
     * <p>잘리면 파싱이 실패해 코드 예측으로 되돌아갑니다. <b>조용히는 아닙니다</b>
     * (I144의 경고가 잡습니다) — 다만 LLM을 부르고도 안 쓴 셈이라 값만 치릅니다.
     */
    private final int maxTokens;
    /**
     * 실거래 표본이 이보다 적으면 <b>LLM이 뭐라 하든 UNCERTAIN</b>입니다.
     * 3건으로는 누구도 알 수 없습니다 — 판단의 문제가 아니라 사실의 문제입니다.
     */
    private static final int MIN_TRADE_SAMPLES = 3;
    private static final String TREND_CODE = "실거래 추세";

    private final LlmPort llmPort;
    private final ForecastIndicatorFactory indicatorFactory;
    private final ForecastVerdictParser parser;
    private final ForecastTradeCollector collector;
    private final LoanRateHistoryPort loanRateHistoryPort;
    private final BuildingLedgerPort buildingLedgerPort;
    private final LandUseRepository landUseRepository;
    private final PropertyRepository propertyRepository;
    private final PriceForecastRepository forecastRepository;
    private final LegalDongCodeService legalDongCodeService;
    private final LlmJobCache jobCache;
    private final boolean enabled;
    private final String model;
    private final int rateLookbackMonths;

    public PriceForecastService(LlmPort llmPort,
                                ForecastIndicatorFactory indicatorFactory,
                                ForecastTradeCollector collector,
                                LoanRateHistoryPort loanRateHistoryPort,
                                BuildingLedgerPort buildingLedgerPort,
                                LandUseRepository landUseRepository,
                                PropertyRepository propertyRepository,
                                PriceForecastRepository forecastRepository,
                                LegalDongCodeService legalDongCodeService,
                                LlmJobCache jobCache,
                                ObjectMapper objectMapper,
                                @Value("${llm.enabled:true}") boolean enabled,
                                @Value("${llm.claude.model.forecast:}") String model,
                                @Value("${forecast.rate-lookback-months:24}") int rateLookbackMonths,
                                @Value("${forecast.max-tokens:4000}") int maxTokens) {
        this.llmPort = llmPort;
        this.indicatorFactory = indicatorFactory;
        this.parser = new ForecastVerdictParser(objectMapper);
        this.collector = collector;
        this.loanRateHistoryPort = loanRateHistoryPort;
        this.buildingLedgerPort = buildingLedgerPort;
        this.landUseRepository = landUseRepository;
        this.propertyRepository = propertyRepository;
        this.forecastRepository = forecastRepository;
        this.legalDongCodeService = legalDongCodeService;
        this.jobCache = jobCache;
        this.enabled = enabled;
        this.model = model == null || model.isBlank() ? null : model;
        this.rateLookbackMonths = rateLookbackMonths;
        this.maxTokens = maxTokens;
    }

    /** 화면이 "지금 분석 중인가"를 물어볼 키 (설계 I72와 같은 방식). */
    public static String jobKey(Long propertyId) {
        return "forecast:" + propertyId;
    }

    /**
     * 매물 하나의 전망을 낸다 — 재료를 모으고, 판단하고, 저장한다 (설계 I135).
     *
     * <p><b>같은 지표면 다시 묻지 않습니다</b>(I59). 60개월 조회는 캐시가 받고,
     * LLM은 프롬프트 해시가 받습니다.
     */
    public Optional<PriceForecast> refresh(Long propertyId) {
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final Property property = found.get();
        jobCache.markRunning(jobKey(propertyId));
        try {
            final ForecastVerdict verdict = forecast(gather(property));
            final String hash = hashToStore(verdict);

            final Optional<PriceForecast> cached = forecastRepository.findByPropertyId(propertyId);
            if (cached.isPresent() && hash != null && hash.equals(cached.get().promptHash())) {
                log.info("Price forecast unchanged - keeping stored verdict. propertyId={}", propertyId);
                return cached;
            }
            final PriceForecast saved = forecastRepository.upsert(new PriceForecast(
                    null, propertyId, verdict.conclusion(), verdict.byCode().direction(),
                    hash, modelToStore(verdict, llmPort.provider()), Instant.now()));
            log.info("Price forecast stored. propertyId={}, direction={}, codeDirection={}, agreed={}",
                    propertyId, saved.outlook().direction(), saved.codeDirection(), saved.agreed());
            return Optional.of(saved);
        } finally {
            // 성공이든 실패든 지운다 (설계 I109). 결과는 DB에 있고, 표시가 남으면
            // 화면이 영영 돕니다 — completed 를 따로 볼 이유가 없습니다
            jobCache.clear(jobKey(propertyId));
        }
    }

    /**
     * 저장할 프롬프트 해시 (설계 I145).
     *
     * <p><b>답을 받았을 때만 남깁니다.</b> 실패한 호출에 해시를 붙이면 다음 호출이
     * "같은 지표니 다시 안 묻는다"(I59)로 건너뛰어, <b>일시적 장애가 영구적인 답이 됩니다.</b>
     *
     * <p>프롬프트가 만들어졌다는 것과 답을 받았다는 것은 다릅니다 — 키가 없어도,
     * 400을 맞아도, 답이 읽히지 않아도 프롬프트는 남습니다.
     */
    static String hashToStore(ForecastVerdict verdict) {
        return verdict.llmAnswered() ? sha256(verdict.prompt().full()) : null;
    }

    /**
     * 저장할 모델 이름 (설계 I145).
     *
     * <p>답을 못 받았으면 <b>비워 둡니다.</b> "claude가 냈다"고 적어 두면
     * 사후 검증(구현 10)이 <b>호출 실패를 모델의 판단으로 세게</b> 됩니다 —
     * 적중률이 통째로 틀어집니다.
     */
    static String modelToStore(ForecastVerdict verdict, String provider) {
        return verdict.llmAnswered() ? provider : null;
    }

    public Optional<PriceForecast> find(Long propertyId) {
        return forecastRepository.findByPropertyId(propertyId);
    }

    /**
     * 목록에 전망 요약을 붙인다 (설계 I136).
     *
     * <p><b>한 번에 읽습니다.</b> 매물마다 따로 부르면 목록의 N+1이 되살아납니다(I124).
     *
     * <p>진행 여부는 캐시를 봐야 하므로 매물마다 확인하지만, 인메모리·Redis 조회라
     * DB 왕복과는 무게가 다릅니다.
     */
    public List<ScoredPropertyResponse> attachForecasts(List<ScoredPropertyResponse> scored) {
        if (scored.isEmpty()) {
            return scored;
        }
        final List<Long> ids = scored.stream().map(s -> s.property().id()).toList();
        final java.util.Map<Long, PriceForecast> forecasts =
                forecastRepository.findByPropertyIds(ids);
        return scored.stream()
                .map(s -> s.withForecast(summaryOf(s.property().id(), forecasts.get(s.property().id()))))
                .toList();
    }

    /** 단건. */
    public ScoredPropertyResponse attachForecast(ScoredPropertyResponse scored) {
        final Long id = scored.property().id();
        return scored.withForecast(summaryOf(id, forecastRepository.findByPropertyId(id).orElse(null)));
    }

    private ForecastSummary summaryOf(Long propertyId, PriceForecast forecast) {
        final boolean running = isRunning(propertyId);
        return forecast == null
                ? ForecastSummary.pending(running)
                : ForecastSummary.from(forecast, running);
    }

    /** 지금 분석 중인가 — 화면 폴링용. */
    public boolean isRunning(Long propertyId) {
        return jobCache.get(jobKey(propertyId))
                .map(banghak.home.halley.domain.llm.LlmJobState::isRunning)
                .orElse(false);
    }

    /**
     * 재료를 모은다.
     *
     * <p>실거래 60개월은 캐시가 받고(I128), 금리는 ECOS(I116), 용도지역은 토지이용계획(I69),
     * 용적률은 건축물대장(I132)입니다. <b>하나가 없어도 나머지로 갑니다.</b>
     */
    private ForecastInput gather(Property property) {
        final String lawdCd = legalDongCodeService.deriveSigunguCode(property.addressJibun())
                .orElse(null);
        final YearMonth now = YearMonth.now();
        final BuildingLedger ledger = buildingLedgerPort.isEnabled()
                ? buildingLedgerPort.fetchRecapTitle(property.pnu()).orElse(null)
                : null;
        return new ForecastInput(
                property,
                collector.collect(lawdCd, CachedDealType.TRADE),
                collector.collect(lawdCd, CachedDealType.JEONSE),
                loanRateHistoryPort.isEnabled()
                        ? loanRateHistoryPort.fetchHouseholdLoanRates(
                                now.minusMonths(rateLookbackMonths), now)
                        : List.of(),
                landUseRepository.findByPropertyId(property.id()),
                ledger);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 지표를 계산하고, 코드와 LLM이 각각 판단한다.
     *
     * <p><b>LLM이 죽어도 지표는 그대로 나옵니다.</b> 그게 지표를 먼저 만든 이유입니다.
     */
    public ForecastVerdict forecast(ForecastInput input) {
        final PriceOutlook byCode = indicatorFactory.forecaster().forecast(input);
        final int horizon = indicatorFactory.horizonMonths();

        if (byCode.factors().isEmpty()) {
            // 재료가 없으면 묻지 않는다 — 일반론이 돌아온다
            log.info("Skipping forecast LLM call - no indicators. propertyId={}",
                    input.property() == null ? null : input.property().id());
            return new ForecastVerdict(byCode, byCode, null, false);
        }
        // 어느 지표가 값을 냈는지 남긴다 (설계 I150). 개수만 남기면 판단이 보류될 때
        // 무엇이 없어서인지 알 수 없다 — 실거래 추세가 빠진 것인지, 전세가율이 빠진 것인지
        log.info("Forecast indicators produced values. names=[{}]", byCode.factors().stream()
                .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                .collect(java.util.stream.Collectors.joining(", ")));
        final ForecastPrompt prompt = ForecastPrompt.of(input.property(), byCode.factors(), horizon);

        if (!enabled || !llmPort.isEnabled()) {
            log.info("Skipping forecast LLM call - provider not enabled. provider={}", llmPort.provider());
            // 키를 나중에 넣으면 다시 물어야 한다. 해시를 남기면 그 기회가 사라진다
            return new ForecastVerdict(byCode, byCode, prompt, false);
        }
        final Optional<PriceOutlook> byLlm = ask(prompt, horizon);
        return byLlm
                .map(llm -> new ForecastVerdict(guard(llm, byCode), byCode, prompt, true))
                // 못 받았으면 코드 예측으로 답하되, 답한 것처럼 굳히지는 않는다
                .orElseGet(() -> new ForecastVerdict(byCode, byCode, prompt, false));
    }

    /**
     * LLM에 묻는다. <b>못 받으면 비어 있다</b> (설계 I145).
     *
     * <p>예전에는 코드 예측을 대신 돌려줬는데, 부른 쪽에서 <b>답을 받은 것과 구분할 수
     * 없었습니다.</b> 그래서 실패한 호출에도 프롬프트 해시가 저장됐고, 다시 물으면
     * 해시가 같아 <b>영영 건너뛰었습니다.</b>
     */
    private Optional<PriceOutlook> ask(ForecastPrompt prompt, int horizon) {
        log.info("Asking LLM for price forecast. knownNumbers={}, promptChars={}",
                prompt.allowedNumbers().size(), prompt.user().length());
        log.debug("Forecast prompt.\n{}", prompt.user());

        final long askedAt = System.currentTimeMillis();
        // 판단 작업이라 흔들리면 안 된다 (설계 I127)
        final LlmResult result = llmPort.complete(
                LlmMessage.deterministic(prompt.system(), prompt.user(), maxTokens, model));
        log.info("LLM forecast responded. present={}, elapsedMs={}",
                result.isPresent(), System.currentTimeMillis() - askedAt);

        if (!result.isPresent()) {
            log.warn("Forecast LLM unavailable - falling back to rule-based. cause={}",
                    result.failureCause());
            return Optional.empty();
        }
        final Optional<PriceOutlook> parsed = parser.parse(result.text(), prompt, horizon);
        if (parsed.isEmpty()) {
            log.warn("Forecast verdict unreadable - falling back to rule-based.");
        }
        return parsed;
    }

    /**
     * 코드가 못 박는 것 (설계 2.2-A).
     *
     * <p>표본이 얇으면 <b>LLM이 뭐라 하든 UNCERTAIN</b>입니다. 3건으로는 누구도 알 수 없으니
     * 판단에 맡길 문제가 아닙니다.
     *
     * <p>요인이 전부 걸러졌다면(지어낸 숫자만 인용했다면) 그 답은 믿을 수 없습니다.
     */
    private PriceOutlook guard(PriceOutlook byLlm, PriceOutlook byCode) {
        if (byLlm.factors().isEmpty() && !byCode.factors().isEmpty()) {
            log.warn("All LLM factors were dropped - falling back to rule-based.");
            return byCode;
        }
        if (!hasEnoughTradeSamples(byCode)) {
            log.info("Forcing UNCERTAIN - trade samples below {}.", MIN_TRADE_SAMPLES);
            final List<String> caveats = new ArrayList<>(byLlm.caveats());
            caveats.add(String.format("실거래 표본이 %d건 미만이라 방향을 판단하지 않았습니다",
                    MIN_TRADE_SAMPLES));
            return new PriceOutlook(ForecastDirection.UNCERTAIN, ForecastConfidence.LOW,
                    byLlm.horizonMonths(), byLlm.factors(), caveats);
        }
        return byLlm;
    }

    /**
     * 실거래 추세 요인이 나왔다는 것은 <b>표본이 충분했다는 뜻</b>입니다 —
     * 지표가 이미 3건 미만이면 내지 않습니다(I130). 그래서 요인의 존재로 가립니다.
     */
    private boolean hasEnoughTradeSamples(PriceOutlook byCode) {
        return byCode.factors().stream().anyMatch(f -> TREND_CODE.equals(f.name()));
    }

    /**
     * 두 예측과 프롬프트.
     *
     * @param conclusion 결론 — LLM이 있으면 LLM, 없으면 코드
     * @param byCode     코드 예측. <b>화면의 참고 문구에만 씁니다</b> (설계 5.2)
     * @param prompt     해시로 중복 호출을 막을 때 쓴다 (설계 I59). 안 부른 경우 null
     * @param llmAnswered LLM이 <b>실제로 답했는가</b> (설계 I145).
     *                   프롬프트가 만들어졌다고 답을 받은 것은 아니다 — 키가 없거나
     *                   400을 맞아도 프롬프트는 남는다. <b>이걸 구분하지 않으면
     *                   실패가 해시로 굳어 다시 물을 수 없게 된다.</b>
     */
    public record ForecastVerdict(PriceOutlook conclusion, PriceOutlook byCode,
                                  ForecastPrompt prompt, boolean llmAnswered) {

        /** 둘이 같은 방향인가 — 모달 문구를 가른다. */
        public boolean agreed() {
            return conclusion.direction() == byCode.direction();
        }

        public List<PriceFactor> factors() {
            return conclusion.factors();
        }
    }
}
