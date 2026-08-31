package banghak.home.halley.domain.forecast;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LLM 답을 읽고 <b>못 믿을 것을 걸러 낸다</b> (설계 I134 · 2.2-A).
 *
 * <p>방향은 LLM이 정하지만, <b>판단이 아니라 사실의 문제</b>인 것은 코드가 강제합니다.
 */
@Slf4j
public class ForecastVerdictParser {

    private final ObjectMapper objectMapper;

    public ForecastVerdictParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param prompt 무엇을 줬는지 — <b>지어낸 숫자를 잡는 기준</b>이다
     * @return 읽을 수 없으면 {@code empty}. 부분적으로 이상하면 그 부분만 버리고 나머지를 살린다
     */
    public Optional<PriceOutlook> parse(String raw, ForecastPrompt prompt, int horizonMonths) {
        final JsonNode node = readJson(raw);
        if (node == null) {
            return Optional.empty();
        }
        final ForecastDirection direction = direction(node.path("direction").asString(null));
        final ForecastConfidence confidence = confidence(node.path("confidence").asString(null));
        final List<PriceFactor> factors = factors(node.path("factors"), prompt);
        final List<String> caveats = caveats(node.path("caveats"), node.path("summary").asString(null));

        return Optional.of(new PriceOutlook(direction, confidence, horizonMonths, factors, caveats));
    }

    /**
     * 모델이 앞뒤에 설명이나 코드펜스를 붙이는 경우가 있어 <b>첫 `{`부터 마지막 `}`까지만</b> 읽는다
     * (AI 추천도와 같은 방식, 설계 I59).
     */
    private JsonNode readJson(String raw) {
        if (raw == null) {
            return null;
        }
        final int start = raw.indexOf('{');
        final int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("Forecast verdict is not JSON. raw={}", abbreviate(raw));
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (RuntimeException e) {
            log.warn("Failed to parse forecast verdict. cause={}, raw={}", e.getMessage(), abbreviate(raw));
            return null;
        }
    }

    /** 모르는 값이면 <b>UNCERTAIN</b>. 모를 때는 판단하지 않은 것으로 본다. */
    private ForecastDirection direction(String value) {
        if (value == null) {
            return ForecastDirection.UNCERTAIN;
        }
        try {
            return ForecastDirection.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown forecast direction - treating as UNCERTAIN. value={}", value);
            return ForecastDirection.UNCERTAIN;
        }
    }

    /** 모르는 값이면 <b>LOW</b>. 모를 때는 낮게 본다. */
    private ForecastConfidence confidence(String value) {
        if (value == null) {
            return ForecastConfidence.LOW;
        }
        try {
            return ForecastConfidence.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown forecast confidence - treating as LOW. value={}", value);
            return ForecastConfidence.LOW;
        }
    }

    /**
     * 요인을 읽되 <b>근거가 없거나 지어낸 숫자를 인용한 것은 버린다.</b>
     *
     * <p>이것이 환각을 잡는 실질적인 장치입니다. 프롬프트로 준 숫자 집합을 들고 있다가
     * 출력의 숫자와 대조합니다.
     */
    private List<PriceFactor> factors(JsonNode array, ForecastPrompt prompt) {
        final List<PriceFactor> factors = new ArrayList<>();
        if (!array.isArray()) {
            return factors;
        }
        for (final JsonNode item : array) {
            final String name = item.path("name").asString(null);
            final String evidence = item.path("evidence").asString(null);
            if (name == null || evidence == null || evidence.isBlank()) {
                log.info("Dropping forecast factor without evidence. name={}", name);
                continue;
            }
            if (!prompt.citesOnlyKnownNumbers(evidence)) {
                // 우리가 주지 않은 숫자를 인용했다 — 지어낸 것이다
                log.warn("Dropping forecast factor citing unknown numbers. name={}, evidence={}",
                        name, evidence);
                continue;
            }
            factors.add(new PriceFactor(name, effect(item), weight(item), evidence));
        }
        return factors;
    }

    private ForecastDirection effect(JsonNode item) {
        final ForecastDirection effect = direction(item.path("effect").asString(null));
        // 요인 하나가 'UNCERTAIN'일 수는 없다 — 방향을 모르면 FLAT 이다
        return effect == ForecastDirection.UNCERTAIN ? ForecastDirection.FLAT : effect;
    }

    private FactorWeight weight(JsonNode item) {
        final String value = item.path("weight").asString(null);
        if (value == null) {
            return FactorWeight.MEDIUM;
        }
        try {
            return FactorWeight.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FactorWeight.MEDIUM;
        }
    }

    /**
     * 요약을 유의사항 앞에 둔다.
     *
     * <p><b>유의사항이 비면 채웁니다.</b> 비워 두면 사용자는 이 판단이 모든 것을 봤다고 여깁니다.
     */
    private List<String> caveats(JsonNode array, String summary) {
        final List<String> caveats = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            caveats.add(summary.trim());
        }
        if (array.isArray()) {
            for (final JsonNode item : array) {
                final String value = item.asString(null);
                if (value != null && !value.isBlank()) {
                    caveats.add(value.trim());
                }
            }
        }
        if (caveats.size() <= 1) {
            caveats.add("정책 변화와 개별 단지의 수급은 반영하지 못했습니다");
        }
        return caveats;
    }

    private String abbreviate(String raw) {
        return raw.length() <= 200 ? raw : raw.substring(0, 200) + "…";
    }
}
