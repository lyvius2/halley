package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.itinerary.TransitLeg;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ODsay 가 하루치를 다 썼을 때 대신 답한다 (설계 I210).
 *
 * <p><b>이것은 추정입니다.</b> LLM 은 시간표를 조회하지 않고 <b>아는 것으로 말합니다</b> —
 * 실제 배차와 다를 수 있고, 없는 노선을 지어낼 수도 있습니다. 그래서
 * <b>그대로 믿지 않습니다</b>: 말이 되는 범위를 벗어나면 버리고 미산출로 둡니다.
 *
 * <p><b>한 번에 묶어 묻습니다.</b> 임장 행렬은 매물 8개면 <b>72쌍</b>인데, 쌍마다
 * 부르면 한 번 계산에 수십 분이 걸립니다. 쌍을 한 프롬프트에 담아 표로 받습니다.
 */
@Slf4j
@Component
public class LlmTransitEstimator {

    /**
     * 한 번에 묶어 물을 수 있는 쌍의 수 (설계 I210).
     *
     * <p>너무 많이 담으면 답이 잘리고, 잘린 답은 <b>뒤쪽 쌍이 통째로 빠집니다.</b>
     * 나눠 부르는 편이 안전합니다.
     */
    private static final int BATCH_SIZE = 20;

    /** 쌍 하나당 넉넉히 잡은 응답 토큰. */
    private static final int TOKENS_PER_PAIR = 120;

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
              <b>지어내지 마십시오.</b> 모른다고 하는 편이 낫습니다.
            - 좌표가 서로 1km 이내면 대개 도보입니다. legs 에 WALK 하나만 두십시오.
            - 주어진 id 를 하나도 빠뜨리지 마십시오.
            """;

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;
    private final String model;

    public LlmTransitEstimator(LlmPort llmPort,
                               ObjectMapper objectMapper,
                               @Value("${transit.fallback.model:}") String model) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public boolean isEnabled() {
        return llmPort.isEnabled();
    }

    /** 좌표 넷으로 한 구간을 가리킨다. {@code id} 는 답을 되돌려 짝지을 열쇠다. */
    public record Leg(String id, double startX, double startY, double endX, double endY) {
    }

    /**
     * 여러 구간을 한꺼번에 (설계 I210).
     *
     * <p>답을 못 받거나 말이 안 되는 쌍은 <b>결과에서 빠집니다</b> — 부르는 쪽이
     * 미산출로 다룹니다. 빈 자리를 0분이나 999분으로 채우지 않습니다.
     */
    public Map<String, TransitResult> estimate(List<Leg> legs) {
        if (legs.isEmpty() || !isEnabled()) {
            return Map.of();
        }
        final Map<String, TransitResult> results = new LinkedHashMap<>();
        for (int from = 0; from < legs.size(); from += BATCH_SIZE) {
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
        final LlmResult answer = llmPort.complete(LlmMessage.deterministic(
                SYSTEM, user.toString(), legs.size() * TOKENS_PER_PAIR,
                model == null || model.isBlank() ? null : model));
        if (answer.failureCause() != null) {
            log.warn("LLM transit fallback failed. legs={}, cause={}", legs.size(), answer.failureCause());
            return Map.of();
        }
        return parse(answer.text(), legs);
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
        // 물어본 것만 받는다. 답에만 있는 id 는 <b>지어낸 것</b>이라 버린다
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
            // 추정임을 값에 실어 보낸다 (설계 I210). 경로선 열쇠(mapObj)는 ODsay 것이라
            // 없다 — 화면이 직선으로 그린다
            parsed.put(id, TransitResult.estimated(
                    total,
                    nz(intOrNull(node.path("transferCount"))),
                    nz(intOrNull(node.path("walkMinutes"))),
                    legsOf(node.path("legs"))));
        }
        return parsed;
    }

    /**
     * 말이 되는 값인가 (설계 I210).
     *
     * <p><b>0분은 안 됩니다.</b> 총점 계산에서 직주근접이 만점이 되는데, 그건
     * "아주 가깝다"가 아니라 <b>LLM 이 답을 못 낸 것</b>일 가능성이 큽니다.
     * 조용히 좋은 쪽으로 틀리는 값이 이 프로젝트에서 가장 위험합니다.
     */
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

    /** 모르는 종류는 버린다 — `valueOf` 로 던지면 답 하나 때문에 구간 전체가 날아간다. */
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
