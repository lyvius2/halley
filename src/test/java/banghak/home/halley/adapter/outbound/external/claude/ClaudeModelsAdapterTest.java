package banghak.home.halley.adapter.outbound.external.claude;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.llm.LlmModelOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델 목록은 <b>이틀에 한 번</b>만 묻는다 (설계 I267).
 *
 * <p>관리자가 설정 화면을 열 때마다 Anthropic 에 묻는 것은 값이 아깝습니다 —
 * 모델 종류는 자주 바뀌지 않습니다.
 */
@DisplayName("Claude 모델 목록 (설계 I267)")
class ClaudeModelsAdapterTest {

    private static final String BODY = """
            {"data":[
              {"id":"claude-opus-5","display_name":"Claude Opus 5"},
              {"id":"claude-haiku-4-5-20251001","display_name":"Claude Haiku 4.5"}
            ]}""";

    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, String> stored = new HashMap<>();
    private Duration storedTtl;

    @Test
    @DisplayName("두 번 물어도 한 번만 받아 온다")
    void asksOnceAndRemembers() {
        final ClaudeModelsAdapter adapter = adapter((key, version, limit) -> {
            calls.incrementAndGet();
            return BODY;
        });

        assertThat(adapter.list()).extracting(LlmModelOption::id)
                .containsExactly("claude-opus-5", "claude-haiku-4-5-20251001");
        adapter.list();

        assertThat(calls.get()).as("설정 화면을 열 때마다 물으면 안 된다").isEqualTo(1);
        assertThat(storedTtl).as("48시간이어야 한다").isEqualTo(Duration.ofHours(48));
    }

    @Test
    @DisplayName("못 받으면 빈 목록 — 그리고 그 실패를 담아 두지 않는다")
    void doesNotCacheAFailure() {
        final ClaudeModelsAdapter adapter = adapter((key, version, limit) -> {
            calls.incrementAndGet();
            return null;
        });

        assertThat(adapter.list()).isEmpty();
        adapter.list();

        // 빈 것을 담으면 <b>한 번 실패한 것을 이틀 동안</b> 물려준다
        assertThat(calls.get()).as("실패를 담아 두면 이틀 동안 못 고친다").isEqualTo(2);
        assertThat(stored).isEmpty();
    }

    private ClaudeModelsAdapter adapter(ClaudeModelsFeignClient client) {
        return new ClaudeModelsAdapter(client, new ObjectMapper(), new CachePort() {

            @Override
            public Optional<String> get(String namespace, String key) {
                return Optional.ofNullable(stored.get(namespace + ":" + key));
            }

            @Override
            public void put(String namespace, String key, String json, Duration ttl) {
                stored.put(namespace + ":" + key, json);
                storedTtl = ttl;
            }

            @Override
            public void evict(String namespace, String key) {
                stored.remove(namespace + ":" + key);
            }

            @Override
            public void evictAll(String namespace) {
                stored.clear();
            }
        }, "test-key");
    }
}
