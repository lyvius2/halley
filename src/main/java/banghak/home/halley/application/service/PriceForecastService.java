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

/** 지표를 놓고 LLM에게 방향을 묻는다. */
@Slf4j
@Service
public class PriceForecastService {

 /** 예산이 모자라면 답이 JSON 중간에서 잘립니다. */
    private final int maxTokens;
 /** 실거래 표본이 이보다 적으면 LLM이 뭐라 하든 UNCERTAIN입니다. */
    private static final int MIN_TRADE_SAMPLES = 3;
 /** 실거래를 실제로 세어 본 지표들. */
    private static final Set<String> TRADE_BASED_FACTORS = Set.of("실거래 추세", "장기 추세");

 /** 실거래가 어디서 걸러졌는지 세는 데만 쓴다 */
    private final banghak.home.halley.domain.forecast.indicator.TradeStatCalculator tradeStats =
            new banghak.home.halley.domain.forecast.indicator.TradeStatCalculator();

 /** 5년의 모양을 한 줄로 */
    private final banghak.home.halley.domain.forecast.indicator.YearlyMedians yearlyMedians =
            new banghak.home.halley.domain.forecast.indicator.YearlyMedians();

    private final LlmPort llmPort;
 /** 전망이 끝난 것을 목록 화면에 알린다 */
    private final ScoreVersionPublisher scoreVersionPublisher;
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
                                ScoreVersionPublisher scoreVersionPublisher,
                                ObjectMapper objectMapper,
                                @Value("${llm.enabled:true}") boolean enabled,
                                LlmModelService llmModelService,
                                @Value("${forecast.rate-lookback-months:24}") int rateLookbackMonths,
                                @Value("${forecast.max-tokens:4000}") int maxTokens) {
        this.llmPort = llmPort;
        this.scoreVersionPublisher = scoreVersionPublisher;
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

 /** 화면이 "지금 분석 중인가"를 물어볼 키. */
    public static String jobKey(Long propertyId) {
        return "forecast:" + propertyId;
    }

 /** 매물 하나의 전망을 낸다. 재료를 모으고, 판단하고, 저장한다. */
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
            jobCache.clear(jobKey(propertyId));
            scoreVersionPublisher.bump(propertyId);
        }
    }

 /** 판정 규칙 판 번호. */
    private static final String VERDICT_RULES_VERSION = "I249";

 /** 저장할 프롬프트 해시. */
    static String hashToStore(ForecastVerdict verdict) {
        return verdict.llmAnswered() ? sha256(VERDICT_RULES_VERSION + "\n" + verdict.prompt().full()) : null;
    }

 /** 저장할 모델 이름. */
    static String modelToStore(ForecastVerdict verdict, String provider) {
        return verdict.llmAnswered() ? provider : null;
    }

    public Optional<PriceForecast> find(Long propertyId) {
        return forecastRepository.findByPropertyId(propertyId);
    }

 /** 목록에 전망 요약을 붙인다. */
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

 /** 지금 분석 중인가. 화면 폴링용. */
    public boolean isRunning(Long propertyId) {
        return jobCache.get(jobKey(propertyId))
                .map(banghak.home.halley.domain.llm.LlmJobState::isRunning)
                .orElse(false);
    }

 /** 재료를 모은다. */
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

 /** 지표를 계산하고, 코드와 LLM이 각각 판단한다. */
    public ForecastVerdict forecast(ForecastInput input) {
        final PriceOutlook byCode = indicatorFactory.forecaster().forecast(input);
        final int horizon = indicatorFactory.horizonMonths();

        if (byCode.factors().isEmpty()) {
            log.info("Skipping forecast LLM call - no indicators. propertyId={}",
                    input.property() == null ? null : input.property().id());
            return new ForecastVerdict(byCode, byCode, null, null, false);
        }
        log.info("Forecast indicators produced values. names=[{}]", byCode.factors().stream()
                .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                .collect(java.util.stream.Collectors.joining(", ")));
        final String gapNote = hasEnoughTradeSamples(byCode) ? null : tradeGapNote(input);
        final String shape = yearlyMedians.describe(input.property(), input.monthlyTrades(),
                input.baseMonth().getYear());
        final ForecastPrompt prompt = ForecastPrompt.of(
                input.property(), byCode.factors(), horizon, gapNote, shape);

        if (!enabled || !llmPort.isEnabled()) {
            log.info("Skipping forecast LLM call - provider not enabled. provider={}", llmPort.provider());
            return new ForecastVerdict(byCode, byCode, null, prompt, false);
        }
        final Optional<PriceOutlook> byLlm = ask(prompt, horizon);
        return byLlm
                .map(llm -> new ForecastVerdict(
                        guard(llm, byCode, input), byCode, llm.direction(), prompt, true))
                .orElseGet(() -> new ForecastVerdict(byCode, byCode, null, prompt, false));
    }

 /** LLM에 묻는다. 못 받으면 비어 있다. */
    private Optional<PriceOutlook> ask(ForecastPrompt prompt, int horizon) {
        final String model = llmModelService.modelFor(LlmFeature.PRICE_FORECAST);
        log.info("Asking LLM for price forecast. model={}, knownNumbers={}, promptChars={}",
                model, prompt.allowedNumbers().size(), prompt.user().length());
        log.debug("Forecast prompt.\n{}", prompt.user());

        final long askedAt = System.currentTimeMillis();
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

 /** 코드가 못 박는 것. */
 /** 실거래 지표가 왜 빠졌는지. */
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

 /** 결론은 지표에서 계산합니다. */
    private PriceOutlook tallied(PriceOutlook outlook, List<String> caveats, boolean thinEvidence) {
        final ForecastDirection said = outlook.direction();
        final boolean committed = said == ForecastDirection.UP || said == ForecastDirection.DOWN;
        final ForecastDirection direction = committed
                ? said
                : FactorTally.of(outlook.factors()).direction();
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

 /** 실거래 표본이 없을 때. */
    private PriceOutlook withoutTradeSamples(PriceOutlook byLlm, PriceOutlook byCode) {
        log.info("No trade-based indicator - falling back to a majority read. required={}, got=[{}]",
                TRADE_BASED_FACTORS, byCode.factors().stream()
                        .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
        return majorityRead(byLlm, byCode, String.format(
                "이 단지·면적대의 실거래 표본이 %d건 미만이라", MIN_TRADE_SAMPLES));
    }

 /** 지표를 세어 방향을 낸다. */

 /** 지표를 세어 방향을 낸다. */
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

 /** 실거래 표본이 있었는가. */
    private boolean hasEnoughTradeSamples(PriceOutlook byCode) {
        return byCode.factors().stream().anyMatch(f -> TRADE_BASED_FACTORS.contains(f.name()));
    }

 /** 두 예측과 프롬프트. */
 /** conclusion 은 규칙까지 거친 최종 결론이라 둘이 다를 수 있다 */
    public record ForecastVerdict(PriceOutlook conclusion, PriceOutlook byCode,
                                  ForecastDirection llmDirection,
                                  ForecastPrompt prompt, boolean llmAnswered) {

 /** 둘이 같은 방향인가. 모달 문구를 가른다. */
        public boolean agreed() {
            return conclusion.direction() == byCode.direction();
        }

        public List<PriceFactor> factors() {
            return conclusion.factors();
        }
    }
}
