package banghak.home.halley.adapter.outbound.external.claude;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.ClaudeModelsPort;
import banghak.home.halley.domain.llm.LlmModelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Anthropic 의 모델 목록 — 48시간 담아 두고, 못 받으면 예외 없이 빈 목록을 돌려준다 (설계 I267). */
@Slf4j
@Component
public class ClaudeModelsAdapter implements ClaudeModelsPort {

    private static final String API_VERSION = "2023-06-01";
    private static final String CACHE_KEY = "claude";
    /** 목록의 수명 — 종류가 자주 바뀌지 않아 이틀이면 넉넉하다. */
    private static final Duration TTL = Duration.ofHours(48);
    /** Anthropic 이 한 번에 주는 최대치. */
    private static final int LIMIT = 100;

    private final ClaudeModelsFeignClient client;
    private final ObjectMapper objectMapper;
    private final CachePort cache;
    private final String apiKey;

    public ClaudeModelsAdapter(ClaudeModelsFeignClient client, ObjectMapper objectMapper, CachePort cache,
                               @Value("${llm.claude.api-key:}") String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.apiKey = apiKey;
    }

    @Override
    public List<LlmModelOption> list() {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        return cache.get(CachePort.LLM_MODELS, CACHE_KEY)
                .map(this::parse)
                .filter(models -> !models.isEmpty())
                .orElseGet(this::fetch);
    }

    private List<LlmModelOption> fetch() {
        final String body = client.models(apiKey, API_VERSION, LIMIT);
        if (body == null) {
            return List.of();
        }
        final List<LlmModelOption> models = parse(body);
        if (!models.isEmpty()) {
            // 빈 목록은 담지 않는다 — 한 번 실패한 것을 이틀 동안 물려주게 된다
            cache.put(CachePort.LLM_MODELS, CACHE_KEY, body, TTL);
        }
        return models;
    }

    private List<LlmModelOption> parse(String body) {
        try {
            final JsonNode data = objectMapper.readTree(body).path("data");
            final List<LlmModelOption> models = new ArrayList<>();
            for (final JsonNode node : data) {
                final String id = node.path("id").asString(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                models.add(LlmModelOption.of(id, node.path("display_name").asString(id)));
            }
            return List.copyOf(models);
        } catch (RuntimeException e) {
            log.warn("Could not read Claude model list. cause={}", e.toString());
            return List.of();
        }
    }
}
