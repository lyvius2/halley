package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 담아 두기를 끌 수 있어야 한다 (설계 I242).
 *
 * <p>DB·Redis 가 <b>앱과 같은 EC2 안 Docker</b> 로 오면서, 이 캐시를 정당화하던
 * "왕복 20ms"가 사라졌습니다. 남길지 걷어낼지는 <b>재고 나서</b> 정할 일인데,
 * 켠 채로만 재면 견줄 것이 없습니다({@code ADJUST_CACHE.md} §5).
 *
 * <p>끈 쪽은 <b>느리기만 하고 답은 같아야</b> 합니다. 껐더니 값이 달라지면
 * 비교 자체가 무의미합니다.
 *
 * <p><b>기본은 켜짐입니다.</b> 캐시를 걷어낼 이유는 없습니다 — 왕복이 싸졌다고
 * 사람이 안 건드리는 표를 요청마다 다시 물을 이유가 생기는 것은 아닙니다.
 *
 * <p>스위치가 <b>실제로 꽂혀 있는지</b>는 {@link ReferenceDataCacheWiringTest} 가 봅니다.
 * 여기서는 클래스를 직접 만들어 보므로, {@code @Value} 의 키를 틀려도 안 걸립니다.
 */
@DisplayName("기준 정보 캐시 켜고 끄기 (설계 I242)")
class ReferenceDataCacheToggleTest {

    private static final TypeReference<List<String>> LIST = new TypeReference<>() { };

    private final CountingCache port = new CountingCache();

    @Test
    @DisplayName("켜면 한 번만 읽고 다음부터는 담아 둔 것을 준다")
    void enabledReadsTheSourceOnce() {
        final ReferenceDataCache cache = new ReferenceDataCache(port, JsonMapper.builder().build(), true);
        final AtomicInteger loads = new AtomicInteger();

        final List<String> first = cache.get(CachePort.CRITERION, "all", LIST, () -> {
            loads.incrementAndGet();
            return List.of("PRICE", "AREA");
        });
        final List<String> second = cache.get(CachePort.CRITERION, "all", LIST, () -> {
            loads.incrementAndGet();
            return List.of("PRICE", "AREA");
        });

        assertThat(first).isEqualTo(second).containsExactly("PRICE", "AREA");
        assertThat(loads.get()).as("두 번째는 원본까지 안 간다").isOne();
    }

    @Test
    @DisplayName("끄면 늘 원본에서 읽는다 — 그래도 값은 같다")
    void disabledAlwaysReadsTheSource() {
        final ReferenceDataCache cache = new ReferenceDataCache(port, JsonMapper.builder().build(), false);
        final AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            final List<String> value = cache.get(CachePort.CRITERION, "all", LIST, () -> {
                loads.incrementAndGet();
                return List.of("PRICE", "AREA");
            });
            assertThat(value)
                    .as("꺼도 답이 달라지면 켠 쪽과 견줄 수 없다")
                    .containsExactly("PRICE", "AREA");
        }

        assertThat(loads.get()).isEqualTo(3);
    }

    /**
     * 껐는데 <b>담아 두기만</b> 하면 더 느려집니다 — 읽지도 않을 것을 매번 씁니다.
     */
    @Test
    @DisplayName("끄면 담아 두지도 않는다")
    void disabledDoesNotEvenWrite() {
        final ReferenceDataCache cache = new ReferenceDataCache(port, JsonMapper.builder().build(), false);

        cache.get(CachePort.CRITERION, "all", LIST, () -> List.of("PRICE"));

        assertThat(port.reads.get()).isZero();
        assertThat(port.writes.get()).as("읽지도 않을 것을 쓰면 느려지기만 한다").isZero();
    }

    /**
     * 껐어도 <b>지우기는 돌아야</b> 합니다 (설계 I242).
     *
     * <p>껐다 켜며 재는 동안 켠 쪽에 <b>옛 값이 남아 있으면</b> 그 뒤 측정이 전부
     * 엉킵니다. 그리고 운영에서 껐다 켠 순간 <b>끄기 전의 값</b>이 살아납니다.
     */
    @Test
    @DisplayName("꺼져 있어도 지우기는 돈다")
    void disabledStillEvicts() {
        final ReferenceDataCache cache = new ReferenceDataCache(port, JsonMapper.builder().build(), false);

        cache.evict(CachePort.CRITERION);

        assertThat(port.evictions.get())
                .as("껐다 켰을 때 끄기 전의 값이 살아나면 안 된다")
                .isOne();
    }

    /** 몇 번 오갔는지만 센다. */
    private static final class CountingCache implements CachePort {

        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger evictions = new AtomicInteger();
        private String held;

        @Override
        public Optional<String> get(String namespace, String key) {
            reads.incrementAndGet();
            return Optional.ofNullable(held);
        }

        @Override
        public void put(String namespace, String key, String json, Duration ttl) {
            writes.incrementAndGet();
            held = json;
        }

        @Override
        public void evict(String namespace, String key) {
            evictions.incrementAndGet();
            held = null;
        }

        @Override
        public void evictAll(String namespace) {
            evictions.incrementAndGet();
            held = null;
        }
    }
}
