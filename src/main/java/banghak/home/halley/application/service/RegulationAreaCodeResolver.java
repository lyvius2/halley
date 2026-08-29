package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 고시의 축약 지역명을 법정동코드 앞 5자리(시군구)로 바꾼다 (설계 I73).
 *
 * <p>고시는 {@code 화성동탄}·{@code 성남분당}처럼 <b>시와 구를 붙여 줄여 씁니다.</b> 어디서 잘라야
 * 하는지 표시가 없어 규칙으로 풀 수 없고({@code 화성/동탄}인지 {@code 화성동/탄}인지 문자열만으로는
 * 모릅니다), 시군구 사전을 코드에 박으면 동탄구처럼 새로 생기는 행정구역을 따라가지 못합니다.
 * 그래서 <b>LLM에 맡깁니다.</b>
 *
 * <p><b>전부 아니면 전무입니다.</b> 하나라도 코드로 못 바꾸면 통째로 실패로 돌립니다. 일부만 넣으면
 * 빠진 지역이 비규제(LTV 0.7)로 잡혀 <b>한도를 과대평가</b>하는데, 그 편이 아무것도 없는 것보다
 * 위험합니다 — 값이 있으니 맞는 줄 알게 됩니다.
 */
@Slf4j
@Service
public class RegulationAreaCodeResolver {

    private static final int MAX_TOKENS = 4096;
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern SIGUNGU_CODE = Pattern.compile("^\\d{5}$");

    private static final String SYSTEM = """
            너는 대한민국 행정구역 코드 전문가다. 국토교통부 규제지역 고시에 적힌 축약 지역명을
            법정동코드 앞 5자리(시군구 코드)로 변환한다.

            고시는 시와 구를 붙여 줄여 쓴다:
              "화성동탄" = 경기도 화성시 동탄구
              "성남분당" = 경기도 성남시 분당구
              "수원장안" = 경기도 수원시 장안구
              "과천"     = 경기도 과천시 (자치구 없음)
              "강남구"   = 서울특별시 강남구

            반드시 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 마라.
            형식: {"입력지역명": {"code": "11680", "name": "서울특별시 강남구"}, ...}

            확신할 수 없는 지역명은 code를 null로 둔다. 추측해서 채우지 마라 —
            틀린 코드는 없는 것보다 나쁘다.
            """;

    private final LlmPort llmPort;
    private final ObjectMapper objectMapper;
    private final String model;

    public RegulationAreaCodeResolver(LlmPort llmPort,
                                      ObjectMapper objectMapper,
                                      @Value("${llm.claude.model.regulation:claude-sonnet-4-6}") String model) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    /**
     * @return 지역명 → (시군구코드, 정식명칭). <b>하나라도 못 풀면 빈 맵</b>을 돌려준다
     */
    public Map<String, ResolvedArea> resolve(List<String> areaNames) {
        if (areaNames == null || areaNames.isEmpty()) {
            return Map.of();
        }
        if (!llmPort.isEnabled()) {
            log.warn("Cannot resolve regulated area codes - LLM not configured. areas={}", areaNames.size());
            return Map.of();
        }
        final LlmResult result = llmPort.complete(new LlmMessage(
                SYSTEM, String.join("\n", areaNames), MAX_TOKENS, model));
        if (!result.isPresent()) {
            log.warn("Regulated area code resolution failed. model={}, reason={}", model, result.failureCause());
            return Map.of();
        }
        final Map<String, ResolvedArea> resolved = parse(result.text());
        final List<String> missing = areaNames.stream().filter(name -> !resolved.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            // 부분 성공을 받아들이면 빠진 지역이 비규제로 잡혀 한도가 과대평가된다
            log.error("Regulated area code resolution incomplete - discarding all {} results. "
                    + "unresolved={}", resolved.size(), missing);
            return Map.of();
        }
        log.info("Regulated area codes resolved. model={}, areas={}", model, resolved.size());
        return resolved;
    }

    private Map<String, ResolvedArea> parse(String text) {
        final Matcher matcher = JSON_OBJECT.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            log.warn("Regulated area code response has no JSON object. text={}", abbreviate(text));
            return Map.of();
        }
        try {
            final JsonNode root = objectMapper.readTree(matcher.group());
            final Map<String, ResolvedArea> resolved = new LinkedHashMap<>();
            for (final Map.Entry<String, JsonNode> entry : root.properties()) {
                final String code = entry.getValue().path("code").asString(null);
                if (code == null || !SIGUNGU_CODE.matcher(code.trim()).matches()) {
                    continue;
                }
                resolved.put(entry.getKey(), new ResolvedArea(
                        code.trim(), entry.getValue().path("name").asString(entry.getKey())));
            }
            return resolved;
        } catch (RuntimeException e) {
            log.warn("Failed to parse regulated area code response. cause={}", e.toString());
            return Map.of();
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }

    /** @param code 법정동코드 앞 5자리 */
    public record ResolvedArea(String code, String name) {
    }
}
