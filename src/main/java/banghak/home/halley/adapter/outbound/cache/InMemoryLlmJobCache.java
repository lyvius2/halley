package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.domain.llm.LlmJobState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * local/개발용 인메모리 구현 (설계 I72). live에서는 {@link RedisLlmJobCache}가 쓰인다.
 *
 * <p>TTL이 다른 이유: RUNNING은 <b>앱이 호출 도중 죽었을 때 남는 찌꺼기</b>라 짧게 두고,
 * DONE은 DB와 같은 값이라 오래 둬도 안전합니다.
 */
@Component
@Profile("!live")
public class InMemoryLlmJobCache implements LlmJobCache {

    static final Duration RUNNING_TTL = Duration.ofMinutes(5);
    static final Duration DONE_TTL = Duration.ofDays(1);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void markRunning(String jobKey) {
        store.put(jobKey, new Entry(LlmJobState.running(), Instant.now().plus(RUNNING_TTL)));
    }

    @Override
    public void markDone(String jobKey, String payload) {
        store.put(jobKey, new Entry(LlmJobState.done(payload), Instant.now().plus(DONE_TTL)));
    }

    @Override
    public Optional<LlmJobState> get(String jobKey) {
        final Entry entry = store.get(jobKey);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            store.remove(jobKey);
            return Optional.empty();
        }
        return Optional.of(entry.state());
    }

    @Override
    public void clear(String jobKey) {
        store.remove(jobKey);
    }

    private record Entry(LlmJobState state, Instant expiresAt) {
    }
}
