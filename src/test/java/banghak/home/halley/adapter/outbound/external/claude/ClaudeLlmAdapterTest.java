package banghak.home.halley.adapter.outbound.external.claude;

import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Claude 어댑터 (설계 I58)")
class ClaudeLlmAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClaudeLlmAdapter adapter(ClaudeFeignClient client, String apiKey) {
        return new ClaudeLlmAdapter(client, objectMapper, apiKey, "claude-sonnet-4-5-20250929", false);
    }

    @Test
    @DisplayName("content 배열의 텍스트 블록만 이어 붙인다")
    void parsesTextBlocks() {
        // given
        final String body = """
                {"id": "msg_1", "type": "message", "role": "assistant",
                 "model": "claude-sonnet-4-5-20250929",
                 "content": [{"type": "text", "text": "{\\"score\\": 78,"},
                             {"type": "text", "text": " \\"reason\\": \\"좋습니다\\"}"}]}
                """;

        // when
        final LlmResult result = adapter(mock(ClaudeFeignClient.class), "key").parse(body);

        // then
        assertThat(result.isPresent()).isTrue();
        assertThat(result.text()).isEqualTo("{\"score\": 78, \"reason\": \"좋습니다\"}");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-5-20250929");
    }

    @Test
    @DisplayName("오류 응답은 실패로 돌려준다 — 예외를 던지지 않는다")
    void returnsFailureOnErrorBody() {
        // given
        final String body = """
                {"type": "error", "error": {"type": "authentication_error",
                 "message": "invalid x-api-key"}}
                """;

        // when
        final LlmResult result = adapter(mock(ClaudeFeignClient.class), "key").parse(body);

        // then — LLM은 보조 입력이라 죽어도 나머지 채점은 그대로 나와야 한다
        assertThat(result.isPresent()).isFalse();
        assertThat(result.failureCause()).isEqualTo("invalid x-api-key");
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 실패로 돌려준다")
    void skipsWhenApiKeyMissing() {
        // given
        final AtomicReference<String> called = new AtomicReference<>();
        final ClaudeFeignClient client = (apiKey, version, body) -> {
            called.set(body);
            return "{}";
        };

        // when
        final LlmResult result = adapter(client, "").complete(new LlmMessage("s", "u", 100));

        // then
        assertThat(result.isPresent()).isFalse();
        assertThat(result.failureCause()).isEqualTo("api key not configured");
        assertThat(called.get()).isNull();
    }

    @Test
    @DisplayName("temperature를 안 주면 아예 보내지 않는다 — 기존 호출의 답이 달라지면 안 된다 (설계 I127)")
    void omitsTemperatureWhenNotGiven() {
        // given
        final AtomicReference<String> sent = new AtomicReference<>();
        final ClaudeFeignClient client = capture(sent);

        // when — 기존 호출은 temperature를 모른다
        adapter(client, "sk-test").complete(new LlmMessage("s", "u", 512));

        // then — 0을 기본값으로 넣으면 AI 추천도의 답이 통째로 바뀐다
        assertThat(objectMapper.readTree(sent.get()).has("temperature")).isFalse();
    }

    @Test
    @DisplayName("판단 작업이어도 기본으로는 temperature를 안 보낸다 — 요즘 모델이 400을 준다 (설계 I144)")
    void omitsTemperatureEvenForDeterministicCallsByDefault() {
        // given
        final AtomicReference<String> sent = new AtomicReference<>();
        final ClaudeFeignClient client = capture(sent);

        // when — 판단 작업이라 temperature 0을 실어 보내려 했다
        adapter(client, "sk-test").complete(
                LlmMessage.deterministic("s", "u", 512, null));

        // then — 그래도 안 나간다. 나가면 아래 응답을 받는다
        //   400 `temperature` is deprecated for this model.
        final var node = objectMapper.readTree(sent.get());
        assertThat(node.has("temperature")).isFalse();
        // 모델을 안 골랐으므로 기본 모델은 그대로다
        assertThat(node.path("model").asString()).isEqualTo("claude-sonnet-4-5-20250929");
    }

    @Test
    @DisplayName("받는 모델로 내려가면 켤 수 있다 — 뜻은 남겨 뒀다 (설계 I144)")
    void sendsTemperatureWhenEnabled() {
        // given
        final AtomicReference<String> sent = new AtomicReference<>();
        final ClaudeLlmAdapter adapter = new ClaudeLlmAdapter(
                capture(sent), objectMapper, "sk-test", "old-model", true);

        // when
        adapter.complete(LlmMessage.deterministic("s", "u", 512, null));

        // then — 없는 노드도 asDouble() 은 0.0 을 준다. 있는지부터 본다
        final var node = objectMapper.readTree(sent.get());
        assertThat(node.has("temperature")).isTrue();
        assertThat(node.path("temperature").asDouble()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("켜도 호출자가 안 정했으면 안 보낸다 — 기존 호출의 답이 달라지면 안 된다 (설계 I127)")
    void enabledStillOmitsWhenCallerDidNotAsk() {
        final AtomicReference<String> sent = new AtomicReference<>();
        final ClaudeLlmAdapter adapter = new ClaudeLlmAdapter(
                capture(sent), objectMapper, "sk-test", "old-model", true);

        adapter.complete(new LlmMessage("s", "u", 512));

        assertThat(objectMapper.readTree(sent.get()).has("temperature")).isFalse();
    }

    /**
     * <p>타입 필터(`"text".equals(...)`) 자체는 이 테스트로 검증되지 않는다 — thinking 블록에는
     * `text` 필드가 없어서 필터를 지워도 결과가 같다. <b>확인해 봤고, 그래서 그렇게 적어 둔다.</b>
     * 여기서 보는 것은 <b>text 가 첫 블록이 아니어도 읽는가</b>이고, 그건 요즘 모델에서
     * 실제로 일어나는 일이다.
     */
    @Test
    @DisplayName("text가 첫 블록이 아니어도 읽는다 — 요즘 모델은 thinking을 먼저 보낸다 (설계 I144)")
    void readsTextEvenWhenItIsNotTheFirstBlock() {
        final var result = adapter((k, v, b) -> """
                {"type":"message","model":"claude-opus-5","stop_reason":"end_turn","content":[
                  {"type":"thinking","thinking":"속으로 따져 본다"},
                  {"type":"text","text":"{\\"direction\\":\\"UP\\"}"}]}
                """, "sk-test").complete(new LlmMessage("s", "u", 512));

        assertThat(result.text()).isEqualTo("{\"direction\":\"UP\"}");
    }

    private ClaudeFeignClient capture(AtomicReference<String> sent) {
        return (apiKey, ver, body) -> {
            sent.set(body);
            return """
                    {"type": "message", "model": "m", "content": [{"type": "text", "text": "ok"}]}
                    """;
        };
    }

    @Test
    @DisplayName("요청 본문에 model·max_tokens·system·messages를 담는다")
    void buildsRequestBody() {
        // given
        final AtomicReference<String> sent = new AtomicReference<>();
        final AtomicReference<String> version = new AtomicReference<>();
        final ClaudeFeignClient client = (apiKey, ver, body) -> {
            sent.set(body);
            version.set(ver);
            return """
                    {"type": "message", "model": "m", "content": [{"type": "text", "text": "ok"}]}
                    """;
        };

        // when
        adapter(client, "sk-test").complete(new LlmMessage("너는 조력자다", "이 매물 어때?", 512));

        // then
        final var node = objectMapper.readTree(sent.get());
        assertThat(node.path("model").asString()).isEqualTo("claude-sonnet-4-5-20250929");
        assertThat(node.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(node.path("system").asString()).isEqualTo("너는 조력자다");
        assertThat(node.path("messages").get(0).path("role").asString()).isEqualTo("user");
        assertThat(node.path("messages").get(0).path("content").asString()).isEqualTo("이 매물 어때?");
        // anthropic-version 헤더는 필수다
        assertThat(version.get()).isEqualTo("2023-06-01");
    }

    @Test
    @DisplayName("호출이 실패해 본문이 null이면 실패로 돌려준다")
    void handlesNullBodyFromFallback() {
        // given — FallbackFactory가 null을 돌려준 상황
        final ClaudeFeignClient client = (apiKey, ver, body) -> null;

        // when
        final LlmResult result = adapter(client, "sk-test").complete(new LlmMessage("s", "u", 100));

        // then
        assertThat(result.isPresent()).isFalse();
        assertThat(result.failureCause()).isEqualTo("call failed");
    }

    @Test
    @DisplayName("공급자 이름과 활성 여부를 알려준다 — 나중에 Ollama와 고르기 위한 것")
    void reportsProviderAndEnablement() {
        assertThat(adapter(mock(ClaudeFeignClient.class), "sk-x").provider()).isEqualTo("claude");
        assertThat(adapter(mock(ClaudeFeignClient.class), "sk-x").isEnabled()).isTrue();
        assertThat(adapter(mock(ClaudeFeignClient.class), "  ").isEnabled()).isFalse();
    }
}
