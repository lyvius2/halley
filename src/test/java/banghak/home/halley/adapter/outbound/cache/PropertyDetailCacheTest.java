package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.PropertyDetailCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("매물 상세 곁가지 캐시 (설계 I158)")
class PropertyDetailCacheTest {

    private final InMemoryPropertyDetailCache cache = new InMemoryPropertyDetailCache();

    @Test
    @DisplayName("담고 꺼낸다")
    void roundTrip() {
        cache.put(PropertyDetailCache.AGENTS, 1L, "[{\"id\":1}]");

        assertThat(cache.get(PropertyDetailCache.AGENTS, 1L)).contains("[{\"id\":1}]");
    }

    @Test
    @DisplayName("갈래가 다르면 섞이지 않는다 — 같은 매물이라도")
    void namespacesDoNotCollide() {
        cache.put(PropertyDetailCache.AGENTS, 1L, "중개사");
        cache.put(PropertyDetailCache.LAND_USE, 1L, "토지이용계획");

        assertThat(cache.get(PropertyDetailCache.AGENTS, 1L)).contains("중개사");
        assertThat(cache.get(PropertyDetailCache.LAND_USE, 1L)).contains("토지이용계획");
    }

    @Test
    @DisplayName("그 매물만 지운다")
    void evictsOneProperty() {
        cache.put(PropertyDetailCache.AGENTS, 1L, "하나");
        cache.put(PropertyDetailCache.AGENTS, 2L, "둘");

        cache.evict(PropertyDetailCache.AGENTS, 1L);

        assertThat(cache.get(PropertyDetailCache.AGENTS, 1L)).isEmpty();
        assertThat(cache.get(PropertyDetailCache.AGENTS, 2L)).contains("둘");
    }

    /**
     * 중개사 정보를 고치면 그 중개사가 붙은 매물 전부가 낡는다.
     * <b>다른 갈래까지 지우면 안 된다</b> — 토지이용계획은 중개사와 무관하다.
     */
    @Test
    @DisplayName("갈래 하나만 통째로 지운다 — 다른 갈래는 남는다")
    void evictsOnlyThatNamespace() {
        cache.put(PropertyDetailCache.AGENTS, 1L, "중개사1");
        cache.put(PropertyDetailCache.AGENTS, 2L, "중개사2");
        cache.put(PropertyDetailCache.LAND_USE, 1L, "토지이용계획");

        cache.evictAll(PropertyDetailCache.AGENTS);

        assertThat(cache.get(PropertyDetailCache.AGENTS, 1L)).isEmpty();
        assertThat(cache.get(PropertyDetailCache.AGENTS, 2L)).isEmpty();
        assertThat(cache.get(PropertyDetailCache.LAND_USE, 1L)).contains("토지이용계획");
    }

    @Test
    @DisplayName("담은 적 없으면 비어 있다")
    void emptyWhenAbsent() {
        assertThat(cache.get(PropertyDetailCache.AGENTS, 999L)).isEmpty();
    }

    @Test
    @DisplayName("TTL은 24시간이다")
    void ttlIsOneDay() {
        assertThat(InMemoryPropertyDetailCache.TTL.toHours()).isEqualTo(24);
    }
}
