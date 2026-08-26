package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.domain.itinerary.TravelMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTravelTimeCacheTest {

    private final InMemoryTravelTimeCache cache = new InMemoryTravelTimeCache();

    @Test
    @DisplayName("저장 후 조회하면 이동시간을 반환한다")
    void putThenGet() {
        // when
        cache.put(TravelMode.TRANSIT, 126.9, 37.5, 127.0, 37.6, 42);

        // then
        assertThat(cache.get(TravelMode.TRANSIT, 126.9, 37.5, 127.0, 37.6)).isEqualTo(42);
    }

    @Test
    @DisplayName("좌표 100m 이내 차이는 같은 키로 캐시 히트한다")
    void roundsToHundredMeters() {
        // given — 0.0002° 차이는 3자리 반올림으로 같은 키
        cache.put(TravelMode.TRANSIT, 126.9002, 37.5002, 127.0002, 37.6002, 42);

        // when
        final Integer cached = cache.get(TravelMode.TRANSIT, 126.9004, 37.5004, 127.0004, 37.6004);

        // then
        assertThat(cached).isEqualTo(42);
    }

    @Test
    @DisplayName("다른 좌표는 캐시 미스(null)를 반환한다")
    void differentKeyMiss() {
        // given
        cache.put(TravelMode.TRANSIT, 126.9, 37.5, 127.0, 37.6, 42);

        // when
        final Integer cached = cache.get(TravelMode.TRANSIT, 127.9, 37.5, 127.0, 37.6);

        // then
        assertThat(cached).isNull();
    }
}
