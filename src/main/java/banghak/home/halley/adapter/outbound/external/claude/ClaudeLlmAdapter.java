package banghak.home.halley.adapter.outbound.external.claude;

import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Claude(Anthropic Messages API) 구현체 (설계 I58).
 *
 * <p>응답 본문은 `content` 배열이고 텍스트 블록만 골라 이어 붙입니다. 오류도 HTTP 200이 아닌
 * 상태코드로 오지만 Feign 예외는 FallbackFactory가 삼키므로, 여기서는 <b>본문이 null인지</b>와
 * `type: "error"`인지를 함께 봅니다.
 */
@Slf4j
@Component
public class ClaudeLlmAdapter implements LlmPort {

    private static final String PROVIDER = "claude";
    private static final String API_VERSION = "2023-06-01";

    private final ClaudeFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeLlmAdapter(ClaudeFeignClient client,
                            ObjectMapper objectMapper,
                            @Value("${llm.claude.api-key:}") String apiKey,
                            @Value("${llm.claude.model:claude-opus-5}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResult complete(LlmMessage message) {
        if (!isEnabled()) {
            return LlmResult.failed("api key not configured");
        }
        final String body = client.messages(apiKey, API_VERSION, requestBody(message));
        if (body == null) {
            return LlmResult.failed("call failed");
        }
        return parse(body);
    }

    private String requestBody(LlmMessage message) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", message.maxTokens());
        if (message.system() != null && !message.system().isBlank()) {
            root.put("system", message.system());
        }
        final ArrayNode messages = root.putArray("messages");
        final ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", message.user());
        return root.toString();
    }

    LlmResult parse(String body) {
        try {
            final JsonNode root = objectMapper.readTree(body);
            if ("error".equals(root.path("type").asString(null))) {
                final String error = root.path("error").path("message").asString("unknown error");
                log.warn("Claude returned an error. message={}", error);
                return LlmResult.failed(error);
            }
            final StringBuilder text = new StringBuilder();
            for (final JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asString(null))) {
                    text.append(block.path("text").asString(""));
                }
            }
            if (text.isEmpty()) {
                log.warn("Claude returned no text block. body={}", body);
                return LlmResult.failed("empty response");
            }
            return LlmResult.of(text.toString(), root.path("model").asString(model));
        } catch (RuntimeException e) {
            log.warn("Failed to parse Claude response. cause={}", e.getMessage());
            return LlmResult.failed(e.getMessage());
        }
    }
}
