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
    private final boolean sendTemperature;

    public ClaudeLlmAdapter(ClaudeFeignClient client,
                            ObjectMapper objectMapper,
                            @Value("${llm.claude.api-key:}") String apiKey,
                            @Value("${llm.claude.model:claude-opus-5}") String model,
                            @Value("${llm.claude.send-temperature:false}") boolean sendTemperature) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.sendTemperature = sendTemperature;
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
        // 호출자가 모델을 고르지 않았으면 기본값을 쓴다 (설계 I73)
        root.put("model", message.model() == null || message.model().isBlank()
                ? model : message.model());
        root.put("max_tokens", message.maxTokens());
        // 요즘 모델은 temperature 를 받지 않는다 (설계 I144).
        //   400 `temperature` is deprecated for this model.
        // 그래서 기본으로 보내지 않는다. 받는 모델로 내려갈 때만 켠다
        if (sendTemperature && message.temperature() != null) {
            root.put("temperature", message.temperature());
        }
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
            // 예산이 모자라 잘렸으면 뒤에서 JSON 파싱이 실패한다. 그때 원인을 못 찾는다 (설계 I144)
            if ("max_tokens".equals(root.path("stop_reason").asString(null))) {
                log.warn("Claude hit the token budget - the answer is cut off. "
                        + "maxTokens may be too small for this model's thinking. usage={}",
                        root.path("usage"));
            }
            return LlmResult.of(text.toString(), root.path("model").asString(model));
        } catch (RuntimeException e) {
            log.warn("Failed to parse Claude response. cause={}", e.getMessage());
            return LlmResult.failed(e.getMessage());
        }
    }
}
