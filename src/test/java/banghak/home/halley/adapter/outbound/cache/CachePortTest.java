package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.CachePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("범용 캐시 (설계 I179)")
class CachePortTest {

    private static final java.time.Duration TTL = java.time.Duration.ofHours(24);

    private final InMemoryCachePort cache = new InMemoryCachePort();

    @Test
    @DisplayName("담고 꺼낸다")
    void roundTrip() {
        cache.put(CachePort.AGENTS, "1", "[{\"id\":1}]", TTL);

        assertThat(cache.get(CachePort.AGENTS, "1")).contains("[{\"id\":1}]");
    }

    @Test
    @DisplayName("갈래가 다르면 섞이지 않는다 — 같은 매물이라도")
    void namespacesDoNotCollide() {
        cache.put(CachePort.AGENTS, "1", "중개사", TTL);
        cache.put(CachePort.LAND_USE, "1", "토지이용계획", TTL);

        assertThat(cache.get(CachePort.AGENTS, "1")).contains("중개사");
        assertThat(cache.get(CachePort.LAND_USE, "1")).contains("토지이용계획");
    }

    @Test
    @DisplayName("그 매물만 지운다")
    void evictsOneProperty() {
        cache.put(CachePort.AGENTS, "1", "하나", TTL);
        cache.put(CachePort.AGENTS, "2", "둘", TTL);

        cache.evict(CachePort.AGENTS, "1");

        assertThat(cache.get(CachePort.AGENTS, "1")).isEmpty();
        assertThat(cache.get(CachePort.AGENTS, "2")).contains("둘");
    }

    /**
     * 중개사 정보를 고치면 그 중개사가 붙은 매물 전부가 낡는다.
     * <b>다른 갈래까지 지우면 안 된다</b> — 토지이용계획은 중개사와 무관하다.
     */
    @Test
    @DisplayName("갈래 하나만 통째로 지운다 — 다른 갈래는 남는다")
    void evictsOnlyThatNamespace() {
        cache.put(CachePort.AGENTS, "1", "중개사1", TTL);
        cache.put(CachePort.AGENTS, "2", "중개사2", TTL);
        cache.put(CachePort.LAND_USE, "1", "토지이용계획", TTL);

        cache.evictAll(CachePort.AGENTS);

        assertThat(cache.get(CachePort.AGENTS, "1")).isEmpty();
        assertThat(cache.get(CachePort.AGENTS, "2")).isEmpty();
        assertThat(cache.get(CachePort.LAND_USE, "1")).contains("토지이용계획");
    }

    @Test
    @DisplayName("담은 적 없으면 비어 있다")
    void emptyWhenAbsent() {
        assertThat(cache.get(CachePort.AGENTS, "999")).isEmpty();
    }

    @Test
    @DisplayName("TTL이 지나면 사라진다 — 수명은 부르는 쪽이 정한다 (설계 I179)")
    void expiresAfterTtl() {
        cache.put(CachePort.AGENTS, "1", "곧 사라진다", java.time.Duration.ofMillis(1));

        await(5);

        assertThat(cache.get(CachePort.AGENTS, "1")).isEmpty();
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
