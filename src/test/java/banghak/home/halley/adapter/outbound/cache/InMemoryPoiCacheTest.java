package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.domain.property.NearbyFacility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPoiCacheTest {

    private final InMemoryPoiCache cache = new InMemoryPoiCache();

    @Test
    @DisplayName("저장한 POI를 같은 매물·같은 버전으로 다시 읽는다")
    void putAndGet() {
        // given
        cache.put(1L, 2, List.of(station()));

        // when
        final List<NearbyFacility> found = cache.get(1L, 2);

        // then
        assertThat(found).singleElement()
                .satisfies(f -> assertThat(f.name()).isEqualTo("무악재역"));
    }

    @Test
    @DisplayName("수집 규칙 버전이 다르면 옛 캐시를 쓰지 않는다 — 규칙 변경 시 전량 재수집")
    void differentSchemaVersionMisses() {
        // given — v2로 수집해 둔 캐시
        cache.put(1L, 2, List.of(station()));

        // when — 규칙이 바뀌어 v3으로 조회
        final List<NearbyFacility> found = cache.get(1L, 3);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("캐시가 없는 매물은 빈 목록을 반환한다")
    void missingPropertyReturnsEmpty() {
        // then
        assertThat(cache.get(999L, 2)).isEmpty();
    }

    private static NearbyFacility station() {
        return NearbyFacility.of(1L, "STATION", "SW8", "무악재역", 300, 6, Instant.now());
    }
}
