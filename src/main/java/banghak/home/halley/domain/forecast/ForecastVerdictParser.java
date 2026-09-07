package banghak.home.halley.domain.forecast;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** LLM 답을 읽고 못 믿을 것을 걸러 낸다. */
@Slf4j
public class ForecastVerdictParser {

    private final ObjectMapper objectMapper;

    public ForecastVerdictParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


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

 /** 모델이 앞뒤에 설명이나 코드펜스를 붙이는 경우가 있어 첫 {부터 마지막 }까지만 읽는다 */
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

 /** 모르는 값이면 UNCERTAIN. 모를 때는 판단하지 않은 것으로 본다. */
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

 /** 모르는 값이면 LOW. 모를 때는 낮게 본다. */
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

 /** 요인을 읽되 근거가 없거나 지어낸 숫자를 인용한 것은 버린다. */
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

 /** 요약을 유의사항 앞에 둔다. */
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
