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

@Slf4j
@Service
public class PriceForecastService {

    private final int maxTokens;
    private static final int MIN_TRADE_SAMPLES = 3;
    private static final Set<String> TRADE_BASED_FACTORS = Set.of("실거래 추세", "장기 추세");

    /** 실거래가 어디서 걸러졌는지 세는 데만 쓴다  */
    private final banghak.home.halley.domain.forecast.indicator.TradeStatCalculator tradeStats =
            new banghak.home.halley.domain.forecast.indicator.TradeStatCalculator();

    /** 5년의 모양을 한 줄로  */
    private final banghak.home.halley.domain.forecast.indicator.YearlyMedians yearlyMedians =
            new banghak.home.halley.domain.forecast.indicator.YearlyMedians();

    private final LlmPort llmPort;
    /** 전망이 끝난 것을 목록 화면에 알린다  */
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

    /** 화면이 "지금 분석 중인가"를 물어볼 키.  */
    public static String jobKey(Long propertyId) {
        return "forecast:" + propertyId;
    }

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
            // 성공이든 실패든 지운다. 결과는 DB에 있고, 표시가 남으면
            // 화면이 영영 돕니다 — completed 를 따로 볼 이유가 없습니다
            jobCache.clear(jobKey(propertyId));
            // 목록이 다시 받도록 판 번호를 올린다.
            // 전망은 채점보다 한참 뒤에 끝나는데 예전에는 아무 신호도 없어,
            // 카드가 「분석 중」 표시(◌)에서 화살표로 바뀌지 않았습니다.
            // 저장했든 그대로든 실패했든 `running` 표시가 풀린 것은 같으므로 여기서 올립니다
            scoreVersionPublisher.bump(propertyId);
        }
    }

    private static final String VERDICT_RULES_VERSION = "I249";

    static String hashToStore(ForecastVerdict verdict) {
        return verdict.llmAnswered() ? sha256(VERDICT_RULES_VERSION + "\n" + verdict.prompt().full()) : null;
    }

    static String modelToStore(ForecastVerdict verdict, String provider) {
        return verdict.llmAnswered() ? provider : null;
    }

    public Optional<PriceForecast> find(Long propertyId) {
        return forecastRepository.findByPropertyId(propertyId);
    }

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

    /** 단건.  */
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

    /** 지금 분석 중인가 — 화면 폴링용.  */
    public boolean isRunning(Long propertyId) {
        return jobCache.get(jobKey(propertyId))
                .map(banghak.home.halley.domain.llm.LlmJobState::isRunning)
                .orElse(false);
    }

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

    public ForecastVerdict forecast(ForecastInput input) {
        final PriceOutlook byCode = indicatorFactory.forecaster().forecast(input);
        final int horizon = indicatorFactory.horizonMonths();

        if (byCode.factors().isEmpty()) {
            // 재료가 없으면 묻지 않는다 — 일반론이 돌아온다
            log.info("Skipping forecast LLM call - no indicators. propertyId={}",
                    input.property() == null ? null : input.property().id());
            return new ForecastVerdict(byCode, byCode, null, null, false);
        }
        // 어느 지표가 값을 냈는지 남긴다. 개수만 남기면 판단이 보류될 때
        // 무엇이 없어서인지 알 수 없다 — 실거래 추세가 빠진 것인지, 전세가율이 빠진 것인지
        log.info("Forecast indicators produced values. names=[{}]", byCode.factors().stream()
                .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                .collect(java.util.stream.Collectors.joining(", ")));
        // 실거래 지표가 빠졌으면 왜 빠졌는지 함께 넘긴다.
        // 안 알려 주면 모델이 "인근 실거래 비교 자료가 없습니다" 처럼 지어낸 추측을 쓴다
        final String gapNote = hasEnoughTradeSamples(byCode) ? null : tradeGapNote(input);
        // 5년이 어떤 모양으로 움직였는지. 지표가 아니라 읽을 재료다
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

    private Optional<PriceOutlook> ask(ForecastPrompt prompt, int horizon) {
        // 자리마다 고른 모델을 쓴다 — 환경변수 하나로 묶여 있었다
        final String model = llmModelService.modelFor(LlmFeature.PRICE_FORECAST);
        log.info("Asking LLM for price forecast. model={}, knownNumbers={}, promptChars={}",
                model, prompt.allowedNumbers().size(), prompt.user().length());
        log.debug("Forecast prompt.\n{}", prompt.user());

        final long askedAt = System.currentTimeMillis();
        // 판단 작업이라 흔들리면 안 된다
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
        // 총량이 아니라 구간마다 안 찬 것이다 — "14건뿐"이라고 하면 틀린 말이 된다
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
        // 이 매물의 실거래가 모자라면 방향은 말하되 확신은 하지 않습니다.
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

    private PriceOutlook tallied(PriceOutlook outlook, List<String> caveats, boolean thinEvidence) {
        final ForecastDirection said = outlook.direction();
        // LLM 이 방향을 말했으면 그대로 따릅니다.
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

    private PriceOutlook withoutTradeSamples(PriceOutlook byLlm, PriceOutlook byCode) {
        log.info("No trade-based indicator - falling back to a majority read. required={}, got=[{}]",
                TRADE_BASED_FACTORS, byCode.factors().stream()
                        .map(banghak.home.halley.domain.forecast.PriceFactor::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
        return majorityRead(byLlm, byCode, String.format(
                "이 단지·면적대의 실거래 표본이 %d건 미만이라", MIN_TRADE_SAMPLES));
    }


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

    private boolean hasEnoughTradeSamples(PriceOutlook byCode) {
        return byCode.factors().stream().anyMatch(f -> TRADE_BASED_FACTORS.contains(f.name()));
    }

    public record ForecastVerdict(PriceOutlook conclusion, PriceOutlook byCode,
                                  ForecastDirection llmDirection,
                                  ForecastPrompt prompt, boolean llmAnswered) {

        /** 둘이 같은 방향인가 — 모달 문구를 가른다.  */
        public boolean agreed() {
            return conclusion.direction() == byCode.direction();
        }

        public List<PriceFactor> factors() {
            return conclusion.factors();
        }
    }
}
