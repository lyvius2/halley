package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.adapter.outbound.external.claude.LlmAvailability;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.application.service.LlmModelService;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.itinerary.TransitLeg;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ODsay 가 하루치를 다 썼을 때 대신 답한다. */
@Slf4j
@Component
public class LlmTransitEstimator {

 /** 한 번에 묶어 물을 수 있는 쌍의 수. */
    private static final int BATCH_SIZE = 20;

 /** 생각에도 예산이 든다. */
    private static final int THINKING_BUDGET = 2000;

 /** 쌍 하나당 실제 JSON 이 차지하는 몫. 구간 상세까지 담아 넉넉히 잡았다. */
    private static final int TOKENS_PER_PAIR = 200;

 /** 붐빌 때 한 번만 더. */
    private static final long RETRY_WAIT_MS = 2000;

 /** 서울·수도권 안에서 대중교통으로 이만큼 넘게 걸리는 곳은 사실상 없다. */
    private static final int MAX_PLAUSIBLE_MINUTES = 300;

    private static final String SYSTEM = """
            당신은 한국 수도권 대중교통 경로 추정기입니다.
            주어진 출발·도착 좌표 쌍마다 지하철·버스로 가는 데 걸리는 시간을 추정하십시오.

            반드시 아래 JSON 만 출력하십시오. 설명·머리말·코드펜스를 붙이지 마십시오.

            {"results":[
              {"id":"<주어진 id 그대로>",
               "totalMinutes":<정수, 문 앞에서 문 앞까지 총 분>,
               "transferCount":<정수, 환승 횟수. 없으면 0>,
               "walkMinutes":<정수, 걷는 시간 합계>,
               "legs":[{"kind":"SUBWAY|BUS|WALK",
                        "lineName":"<7호선 · 146 등. 도보면 null>",
                        "from":"<탄 곳. 도보면 null>",
                        "to":"<내린 곳. 도보면 null>",
                        "minutes":<정수>,
                        "stationCount":<정거장 수. 모르면 null>}]}
            ]}

            규칙:
            - 갈 수 없거나 확신이 없으면 그 id 의 totalMinutes 를 null 로 두십시오.
              지어내지 마십시오. 모른다고 하는 편이 낫습니다.
            - 좌표가 서로 1km 이내면 대개 도보입니다. legs 에 WALK 하나만 두십시오.
            - 주어진 id 를 하나도 빠뜨리지 마십시오.
            """;

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;
 /** 이 자리에 쓸 모델을 부를 때마다 물어본다. 붙박이가 아니다. */
    private final LlmModelService llmModelService;

 /** 차단기가 열려 있으면 묻지도 않는다. */
    private final LlmAvailability availability;

    public LlmTransitEstimator(LlmPort llmPort,
                               ObjectMapper objectMapper,
                               LlmModelService llmModelService,
                               LlmAvailability availability) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
        this.llmModelService = llmModelService;
        this.availability = availability;
    }

    public boolean isEnabled() {
        return llmPort.isEnabled();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

 /** 좌표 넷으로 한 구간을 가리킨다. id 는 답을 되돌려 짝지을 열쇠다. */
    public record Leg(String id, double startX, double startY, double endX, double endY) {
    }

 /** 여러 구간을 한꺼번에. */
    public Map<String, TransitResult> estimate(List<Leg> legs) {
        if (legs.isEmpty() || !isEnabled()) {
            return Map.of();
        }
        if (availability.blocked()) {
            log.info("Claude circuit is open - not estimating transit. legs={}", legs.size());
            return Map.of();
        }
        final Map<String, TransitResult> results = new LinkedHashMap<>();
        for (int from = 0; from < legs.size(); from += BATCH_SIZE) {
            if (availability.blocked()) {
                log.info("Claude circuit opened mid-way - stopping. answered={}, asked={}",
                        results.size(), legs.size());
                break;
            }
            final List<Leg> chunk = legs.subList(from, Math.min(from + BATCH_SIZE, legs.size()));
            results.putAll(askOnce(chunk));
        }
        log.info("LLM transit fallback answered {} of {} legs", results.size(), legs.size());
        return results;
    }

    private Map<String, TransitResult> askOnce(List<Leg> legs) {
        final StringBuilder user = new StringBuilder("다음 구간들의 대중교통 소요시간을 추정하십시오.\n\n");
        for (final Leg leg : legs) {
            user.append(String.format("id=%s 출발=(경도 %.6f, 위도 %.6f) 도착=(경도 %.6f, 위도 %.6f)%n",
                    leg.id(), leg.startX(), leg.startY(), leg.endX(), leg.endY()));
        }
        final String model = blankToNull(llmModelService.modelFor(LlmFeature.COMMUTE_ESTIMATE));
        final LlmMessage message = LlmMessage.deterministic(
                SYSTEM, user.toString(), THINKING_BUDGET + legs.size() * TOKENS_PER_PAIR, model);
        log.info("Asking LLM for transit estimate. model={}, legs={}", model, legs.size());

        LlmResult answer = llmPort.complete(message);
        if (retryable(answer)) {
            log.info("LLM is busy - waiting {}ms and asking once more. legs={}", RETRY_WAIT_MS, legs.size());
            sleep();
            answer = llmPort.complete(message);
        }
        if (answer.failureCause() != null) {
            log.warn("LLM transit fallback failed. legs={}, cause={}", legs.size(), answer.failureCause());
            return Map.of();
        }
        return parse(answer.text(), legs);
    }

 /** 다시 물어볼 만한 실패인가. */
 /** 차단기가 열렸으면 다시 묻지 않습니다. 어댑터가 모든 실패를 */
    private boolean retryable(LlmResult answer) {
        return "call failed".equals(answer.failureCause()) && !availability.blocked();
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, TransitResult> parse(String text, List<Leg> asked) {
        final Map<String, TransitResult> parsed = new LinkedHashMap<>();
        final JsonNode root;
        try {
            root = objectMapper.readTree(jsonOf(text));
        } catch (JacksonException e) {
            log.warn("LLM transit fallback returned unparsable text. head={}",
                    text == null ? "null" : text.substring(0, Math.min(200, text.length())));
            return Map.of();
        }
        final java.util.Set<String> wanted = asked.stream().map(Leg::id)
                .collect(java.util.stream.Collectors.toSet());
        for (final JsonNode node : root.path("results")) {
            final String id = node.path("id").asString(null);
            if (id == null || !wanted.contains(id)) {
                continue;
            }
            final Integer total = intOrNull(node.path("totalMinutes"));
            if (!isPlausible(total)) {
                continue;
            }
            parsed.put(id, TransitResult.estimated(
                    total,
                    nz(intOrNull(node.path("transferCount"))),
                    nz(intOrNull(node.path("walkMinutes"))),
                    legsOf(node.path("legs"))));
        }
        return parsed;
    }

 /** 말이 되는 값인가. */
    private static boolean isPlausible(Integer minutes) {
        return minutes != null && minutes > 0 && minutes <= MAX_PLAUSIBLE_MINUTES;
    }

    private List<TransitLeg> legsOf(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        final List<TransitLeg> legs = new ArrayList<>();
        for (final JsonNode leg : node) {
            final TransitLeg.Kind kind = kindOf(leg.path("kind").asString(""));
            if (kind == null) {
                continue;
            }
            legs.add(new TransitLeg(kind,
                    leg.path("lineName").asString(null),
                    leg.path("from").asString(null),
                    leg.path("to").asString(null),
                    intOrNull(leg.path("minutes")),
                    intOrNull(leg.path("stationCount"))));
        }
        return legs;
    }

 /** 모르는 종류는 버린다. valueOf 로 던지면 답 하나 때문에 구간 전체가 날아간다. */
    private static TransitLeg.Kind kindOf(String raw) {
        for (final TransitLeg.Kind kind : TransitLeg.Kind.values()) {
            if (kind.name().equals(raw)) {
                return kind;
            }
        }
        return null;
    }

 /** 코드펜스를 두르지 말라고 했어도 두르는 일이 있다. */
    private static String jsonOf(String text) {
        if (text == null) {
            return "{}";
        }
        final int start = text.indexOf('{');
        final int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : "{}";
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    private static Integer nz(Integer value) {
        return value == null ? 0 : value;
    }
}
