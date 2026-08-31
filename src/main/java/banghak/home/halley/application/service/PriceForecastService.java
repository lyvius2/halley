package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.LlmPort;
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

import java.util.ArrayList;
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

    private static final int MAX_TOKENS = 1500;
    /**
     * 실거래 표본이 이보다 적으면 <b>LLM이 뭐라 하든 UNCERTAIN</b>입니다.
     * 3건으로는 누구도 알 수 없습니다 — 판단의 문제가 아니라 사실의 문제입니다.
     */
    private static final int MIN_TRADE_SAMPLES = 3;
    private static final String TREND_CODE = "실거래 추세";

    private final LlmPort llmPort;
    private final ForecastIndicatorFactory indicatorFactory;
    private final ForecastVerdictParser parser;
    private final boolean enabled;
    private final String model;

    public PriceForecastService(LlmPort llmPort,
                                ForecastIndicatorFactory indicatorFactory,
                                ObjectMapper objectMapper,
                                @Value("${llm.enabled:true}") boolean enabled,
                                @Value("${llm.claude.model.forecast:}") String model) {
        this.llmPort = llmPort;
        this.indicatorFactory = indicatorFactory;
        this.parser = new ForecastVerdictParser(objectMapper);
        this.enabled = enabled;
        this.model = model == null || model.isBlank() ? null : model;
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
            return new ForecastVerdict(byCode, byCode, null);
        }
        final ForecastPrompt prompt = ForecastPrompt.of(input.property(), byCode.factors(), horizon);

        if (!enabled || !llmPort.isEnabled()) {
            log.info("Skipping forecast LLM call - provider not enabled. provider={}", llmPort.provider());
            return new ForecastVerdict(byCode, byCode, prompt);
        }
        final PriceOutlook byLlm = ask(prompt, horizon, byCode);
        return new ForecastVerdict(guard(byLlm, byCode), byCode, prompt);
    }

    private PriceOutlook ask(ForecastPrompt prompt, int horizon, PriceOutlook fallback) {
        log.info("Asking LLM for price forecast. factors={}, promptChars={}",
                prompt.allowedNumbers().size(), prompt.user().length());
        log.debug("Forecast prompt.\n{}", prompt.user());

        final long askedAt = System.currentTimeMillis();
        // 판단 작업이라 흔들리면 안 된다 (설계 I127)
        final LlmResult result = llmPort.complete(
                LlmMessage.deterministic(prompt.system(), prompt.user(), MAX_TOKENS, model));
        log.info("LLM forecast responded. present={}, elapsedMs={}",
                result.isPresent(), System.currentTimeMillis() - askedAt);

        if (!result.isPresent()) {
            log.warn("Forecast LLM unavailable - falling back to rule-based. cause={}",
                    result.failureCause());
            return fallback;
        }
        final Optional<PriceOutlook> parsed = parser.parse(result.text(), prompt, horizon);
        if (parsed.isEmpty()) {
            log.warn("Forecast verdict unreadable - falling back to rule-based.");
            return fallback;
        }
        return parsed.get();
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
     */
    public record ForecastVerdict(PriceOutlook conclusion, PriceOutlook byCode, ForecastPrompt prompt) {

        /** 둘이 같은 방향인가 — 모달 문구를 가른다. */
        public boolean agreed() {
            return conclusion.direction() == byCode.direction();
        }

        public List<PriceFactor> factors() {
            return conclusion.factors();
        }
    }
}
