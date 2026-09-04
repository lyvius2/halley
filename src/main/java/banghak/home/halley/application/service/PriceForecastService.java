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
import banghak.home.halley.domain.forecast.FactorTally;
import banghak.home.halley.domain.forecast.ForecastConfidence;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.ForecastPrompt;
import banghak.home.halley.domain.forecast.ForecastVerdictParser;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.forecast.PriceOutlook;
import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.llm.LlmFeature;
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
import java.util.Set;

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
    /**
     * 실거래를 <b>실제로 세어 본</b> 지표들 (설계 I151).
     *
     * <p>둘 중 하나라도 값을 냈으면 표본이 있었다는 뜻입니다 — 각 지표가 이미
     * 3건 미만이면 내지 않습니다(I130 · I148). 그래서 요인의 존재로 가립니다.
     *
     * <p>금리 국면과 용적률 여유는 <b>여기 없습니다.</b> 그 둘은 실거래를 안 봅니다 —
     * ECOS 통계와 건축물대장이라 아무리 나와도 <b>이 매물의 표본</b>과는 무관합니다.
     */
    private static final Set<String> TRADE_BASED_FACTORS = Set.of("실거래 추세", "장기 추세");

    /** 실거래가 어디서 걸러졌는지 세는 데만 쓴다 (설계 I253) */
    private final banghak.home.halley.domain.forecast.indicator.TradeStatCalculator tradeStats =
            new banghak.home.halley.domain.forecast.indicator.TradeStatCalculator();

    /** 5년의 모양을 한 줄로 (설계 I255) */
    private final banghak.home.halley.domain.forecast.indicator.YearlyMedians yearlyMedians =
            new banghak.home.halley.domain.forecast.indicator.YearlyMedians();

    private final LlmPort llmPort;
    private final LlmModelService llmModelService;
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
                                LlmModelService llmModelService,
                                @Value("${forecast.rate-lookback-months:24}") int rateLookbackMonths,
                                @Value("${forecast.max-tokens:4000}") int maxTokens) {
        this.llmPort = llmPort;
        this.llmModelService = llmModelService;
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
                    null, propertyId, verdict.conclusion(), verdict.llmDirection(),
                    verdict.byCode().direction(),
                    hash, modelToStore(verdict, llmPort.provider()), Instant.now()));
            log.info("Price forecast stored. propertyId={}, direction={}, llmDirection={}, "
                            + "codeDirection={}, strong={}",
                    propertyId, saved.outlook().direction(), saved.llmDirection(),
                    saved.codeDirection(), saved.strong());
            return Optional.of(saved);
        } finally {
            // 성공이든 실패든 지운다 (설계 I109). 결과는 DB에 있고, 표시가 남으면
            // 화면이 영영 돕니다 — completed 를 따로 볼 이유가 없습니다
            jobCache.clear(jobKey(propertyId));
        }
    }

    /**
     * 판정 규칙 판 번호 (설계 I250).
     *
     * <p><b>규칙을 고치면 이 값을 올립니다.</b> 그러면 해시가 달라져 전부 다시 냅니다.
     *
     * <p>[I59]의 "같은 입력이면 다시 안 묻는다"는 <b>입력이 같아도 규칙이 바뀌면
     * 다시 내야 한다</b>는 경우를 못 가렸습니다. [I234]에서 다수결을 넣고,
     * [I248]에서 셈법을 다시 짜고, [I249]에서 AI 우선을 넣었는데 — 지표가 그대로면
     * 프롬프트도 그대로라 해시가 같았고, <b>새 판정을 계산해 놓고 버렸습니다.</b>
     * 그래서 화면은 몇 번을 고쳐도 옛 결론 그대로였습니다.
     *
     * <p>채점이 {@code scoreVersion} 으로 푼 문제와 같습니다([I85]).
     *
     * <p><b>프롬프트를 고칠 때는 안 올려도 됩니다</b> — 그건 해시가 이미 잡습니다.
     * 올릴 때는 <b>지표를 읽는 방식이나 결론을 정하는 방식</b>이 바뀌었을 때입니다.
     */
    private static final String VERDICT_RULES_VERSION = "I249";

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
        return verdict.llmAnswered() ? sha256(VERDICT_RULES_VERSION + "\n" + verdict.prompt().full()) : null;
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

    static String sha256(String value) {
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
            return new ForecastVerdict(byCode, byCode, null, null, false);
        }
        // 어느 지표가 값을 냈는지 남긴다 (설계 I150). 개수만 남기면 판단이 보류될 때
        // 무엇이 없어서인지 알 수 없다 — 실거래 추세가 빠진 것인지, 전세가율이 빠진 것인지
        log.info("Forecast indicators produced values. names=[{}]", byCode.factors().stream()
                .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                .collect(java.util.stream.Collectors.joining(", ")));
        // 실거래 지표가 빠졌으면 <b>왜</b> 빠졌는지 함께 넘긴다 (설계 I253).
        // 안 알려 주면 모델이 "인근 실거래 비교 자료가 없습니다" 처럼 지어낸 추측을 쓴다
        final String gapNote = hasEnoughTradeSamples(byCode) ? null : tradeGapNote(input);
        // 5년이 어떤 모양으로 움직였는지 (설계 I255). 지표가 아니라 읽을 재료다
        final String shape = yearlyMedians.describe(input.property(), input.monthlyTrades(),
                input.baseMonth().getYear());
        final ForecastPrompt prompt = ForecastPrompt.of(
                input.property(), byCode.factors(), horizon, gapNote, shape);

        if (!enabled || !llmPort.isEnabled()) {
            log.info("Skipping forecast LLM call - provider not enabled. provider={}", llmPort.provider());
            // 키를 나중에 넣으면 다시 물어야 한다. 해시를 남기면 그 기회가 사라진다
            return new ForecastVerdict(byCode, byCode, null, prompt, false);
        }
        final Optional<PriceOutlook> byLlm = ask(prompt, horizon);
        return byLlm
                .map(llm -> new ForecastVerdict(
                        guard(llm, byCode, input), byCode, llm.direction(), prompt, true))
                // 못 받았으면 코드 예측으로 답하되, 답한 것처럼 굳히지는 않는다
                .orElseGet(() -> new ForecastVerdict(byCode, byCode, null, prompt, false));
    }

    /**
     * LLM에 묻는다. <b>못 받으면 비어 있다</b> (설계 I145).
     *
     * <p>예전에는 코드 예측을 대신 돌려줬는데, 부른 쪽에서 <b>답을 받은 것과 구분할 수
     * 없었습니다.</b> 그래서 실패한 호출에도 프롬프트 해시가 저장됐고, 다시 물으면
     * 해시가 같아 <b>영영 건너뛰었습니다.</b>
     */
    private Optional<PriceOutlook> ask(ForecastPrompt prompt, int horizon) {
        // 자리마다 고른 모델을 쓴다 (설계 I267) — 환경변수 하나로 묶여 있었다
        final String model = llmModelService.modelFor(LlmFeature.PRICE_FORECAST);
        log.info("Asking LLM for price forecast. model={}, knownNumbers={}, promptChars={}",
                model, prompt.allowedNumbers().size(), prompt.user().length());
        log.debug("Forecast prompt.\n{}", prompt.user());

        final long askedAt = System.currentTimeMillis();
        // 판단 작업이라 흔들리면 안 된다 (설계 I127)
        final LlmResult result = llmPort.complete(
                LlmMessage.deterministic(prompt.system(), prompt.user(), maxTokens, model));
        log.info("LLM forecast responded. model={}, present={}, elapsedMs={}",
                model, result.isPresent(), System.currentTimeMillis() - askedAt);

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
    /**
     * 실거래 지표가 <b>왜</b> 빠졌는지 (설계 I253).
     *
     * <p>이유가 넷인데 화면도 로그도 아무 말이 없었습니다 — 자료를 못 받았는지,
     * 단지명이 안 맞는지, 평형이 다른지, 그냥 거래가 드문지.
     * 사람이 LLM 산문을 읽고 짐작해야 했습니다.
     *
     * <p><b>이 문장은 LLM 에도 갑니다.</b> 이유를 알려 주면 "인근 실거래 비교 자료가
     * 없습니다" 같은 <b>지어낸 추측</b> 대신 정확한 이유를 씁니다.
     */
    String tradeGapNote(ForecastInput input) {
        final var tally = tradeStats.tally(input.property(), input.monthlyTrades());
        final String name = input.property() == null || input.property().name() == null
                ? "이 매물" : input.property().name();
        if (tally.trades() == 0) {
            return "이 지역의 실거래 자료를 받아 두지 못해 실거래 지표를 넣지 못했습니다";
        }
        if (tally.nameMatched() == 0) {
            return String.format("실거래 %d건을 받았지만 '%s'과 이름이 맞는 거래가 없어 "
                    + "실거래 지표를 넣지 못했습니다 — 국토부 표기가 다를 수 있습니다",
                    tally.trades(), name);
        }
        if (tally.areaMatched() == 0) {
            return String.format("실거래 %d건 중 이름이 맞는 것은 %d건이지만 전용 %s㎡와 맞는 "
                    + "평형이 없어 실거래 지표를 넣지 못했습니다",
                    tally.trades(), tally.nameMatched(),
                    input.property() == null ? "?" : String.valueOf(input.property().areaExclusiveM2()));
        }
        // 총량이 아니라 <b>구간마다</b> 안 찬 것이다 — "14건뿐"이라고 하면 틀린 말이 된다
        return String.format("이름·면적이 맞는 실거래는 %d건이지만 비교 구간마다 %d건을 "
                + "채우지 못해 실거래 지표를 넣지 못했습니다 — 거래가 여러 달에 흩어져 "
                + "있습니다", tally.areaMatched(), MIN_TRADE_SAMPLES);
    }

    private PriceOutlook guard(PriceOutlook byLlm, PriceOutlook byCode, ForecastInput input) {
        if (byLlm.factors().isEmpty() && !byCode.factors().isEmpty()) {
            log.warn("All LLM factors were dropped - falling back to rule-based.");
            return tallied(byCode, new ArrayList<>(byCode.caveats()), false);
        }
        final List<String> caveats = new ArrayList<>(byLlm.caveats());
        // 이 매물의 실거래가 모자라면 <b>방향은 말하되 확신은 하지 않습니다</b> (설계 I151).
        // 금리 국면은 ECOS 통계라 아무리 나와도 이 단지의 표본과는 무관합니다
        final boolean thinEvidence = !hasEnoughTradeSamples(byCode);
        if (thinEvidence) {
            log.info("No trade-based indicator - counting the rest. required={}, got=[{}]",
                    TRADE_BASED_FACTORS, byCode.factors().stream()
                            .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                            .collect(java.util.stream.Collectors.joining(", ")));
            caveats.add(tradeGapNote(input));
        }
        return tallied(byLlm, caveats, thinEvidence);
    }

    /**
     * 결론은 <b>지표에서 계산합니다</b> (설계 I248).
     *
     * <p>LLM 이 스스로 낸 방향은 쓰지 않습니다 — LLM 은 <b>지표와 근거만</b> 주고
     * 판정은 우리가 합니다. 규칙이 전부 "지표 중에서"로 되어 있어서입니다.
     *
     * <p>이렇게 하면 <b>화면에 보이는 화살표들과 결론이 어긋날 수 없습니다.</b>
     * 전에는 지표가 ▲▼▼▲ 인데 결론이 "판단 보류"로 떴습니다 — 세어 보면 2:2 동수라
     * 상승이어야 했습니다.
     */
    private PriceOutlook tallied(PriceOutlook outlook, List<String> caveats, boolean thinEvidence) {
        final ForecastDirection said = outlook.direction();
        // <b>LLM 이 방향을 말했으면 그대로 따릅니다</b> (설계 I249).
        // 유지·판단 보류는 "방향을 말하지 않은 것"입니다 — 그때만 우리가 셉니다
        final boolean committed = said == ForecastDirection.UP || said == ForecastDirection.DOWN;
        final ForecastDirection direction = committed
                ? said
                : FactorTally.of(outlook.factors()).direction();
        // 표본이 얇거나 우리가 대신 정했으면 확신도를 낮춥니다.
        // 우리가 세어 넣은 판단에 "확신도 높음"을 붙일 수는 없습니다
        final ForecastConfidence confidence = (thinEvidence || !committed)
                ? ForecastConfidence.LOW
                : outlook.confidence();
        if (!committed) {
            caveats.add("AI가 방향을 정하지 못해 지표가 가리키는 쪽을 세어 정했습니다 "
                    + "— 확신이 있어서가 아닙니다");
        }
        return new PriceOutlook(direction, confidence,
                outlook.horizonMonths(), outlook.factors(), caveats);
    }

    /**
     * 실거래 표본이 없을 때 (설계 I234).
     *
     * <p>전에는 곧바로 `UNCERTAIN` 이었습니다. 그런데 실제로 써 보니
     * <b>거의 모든 매물이 판단 보류</b>였습니다 — 금리·전세가율·용도지역 같은
     * 지표가 <b>여럿 나와 있는데도</b> 그랬습니다. 알아낸 것을 안 보여 준 셈입니다.
     *
     * <p>이제 <b>지표들이 가리키는 쪽을 세어</b> 말하되, <b>확신도는 낮게</b> 두고
     * 무엇이 빠졌는지 함께 적습니다. 방향을 감추는 것과 근거를 밝히는 것 중
     * 뒤쪽이 낫습니다.
     */
    private PriceOutlook withoutTradeSamples(PriceOutlook byLlm, PriceOutlook byCode) {
        log.info("No trade-based indicator - falling back to a majority read. required={}, got=[{}]",
                TRADE_BASED_FACTORS, byCode.factors().stream()
                        .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
        return majorityRead(byLlm, byCode, String.format(
                "이 단지·면적대의 실거래 표본이 %d건 미만이라", MIN_TRADE_SAMPLES));
    }

    /**
     * 지표를 세어 방향을 낸다 (설계 I234).
     *
     * <p>LLM 이 낸 요인을 먼저 봅니다 — 그게 결론의 근거로 화면에 뜨는 것입니다.
     * 비어 있으면 규칙 예측의 요인을 씁니다.
     *
     * <p><b>확신도는 언제나 LOW 입니다.</b> 세어서 고른 것이지 확신이 있어서가 아닙니다.
     */

    /**
     * 지표를 세어 방향을 낸다 (설계 I234).
     *
     * <p>LLM 이 낸 요인을 먼저 봅니다 — 그게 결론의 근거로 화면에 뜨는 것입니다.
     * 비어 있으면 규칙 예측의 요인을 씁니다.
     *
     * <p><b>확신도는 언제나 LOW 입니다.</b> 세어서 고른 것이지 확신이 있어서가 아닙니다.
     */
    private PriceOutlook majorityRead(PriceOutlook byLlm, PriceOutlook byCode, String because) {
        final List<banghak.home.halley.domain.forecast.PriceFactor> factors =
                byLlm.factors().isEmpty() ? byCode.factors() : byLlm.factors();
        final ForecastDirection majority = ForecastDirection.majorityOf(factors);
        final List<String> caveats = new ArrayList<>(byLlm.caveats());
        if (majority == ForecastDirection.UNCERTAIN) {
            caveats.add(because + " 방향을 판단하지 않았습니다");
            return new PriceOutlook(ForecastDirection.UNCERTAIN, ForecastConfidence.LOW,
                    byLlm.horizonMonths(), factors, caveats);
        }
        caveats.add(because + " 지표 " + factors.size() + "개가 가리키는 쪽을 세어 정했습니다 "
                + "— 확신이 있어서가 아닙니다");
        return new PriceOutlook(majority, ForecastConfidence.LOW,
                byLlm.horizonMonths(), factors, caveats);
    }

    /**
     * 실거래 표본이 있었는가 (설계 I151).
     *
     * <p>예전에는 <b>실거래 추세 하나만</b> 봤습니다. 그 지표는 3개월 창이라,
     * 장기 표본이 넉넉해도 <b>최근 석 달이 한산하면</b> 판단이 덮였습니다.
     *
     * <p>§2.2-A의 취지는 "3건으로는 누구도 알 수 없다"입니다 — <b>3개월 창이 얇은 것</b>과
     * <b>실거래 자료가 없는 것</b>은 다른 얘기인데 둘을 같게 보고 있었습니다.
     * 장기 추세(12개월 창 둘)가 나왔다면 표본은 이미 충분합니다.
     */
    private boolean hasEnoughTradeSamples(PriceOutlook byCode) {
        return byCode.factors().stream().anyMatch(f -> TRADE_BASED_FACTORS.contains(f.name()));
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
    /**
     * @param llmDirection LLM 이 <b>스스로 낸</b> 결론 (설계 I249). 답을 못 받았으면 null.
     *                     {@code conclusion} 은 규칙까지 거친 최종 결론이라 둘이 다를 수 있다
     */
    public record ForecastVerdict(PriceOutlook conclusion, PriceOutlook byCode,
                                  ForecastDirection llmDirection,
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
