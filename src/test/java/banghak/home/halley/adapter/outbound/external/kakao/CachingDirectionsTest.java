package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.itinerary.DriveRoute;
import banghak.home.halley.domain.itinerary.RoutePath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한 번 받은 길은 <b>다시 안 받는다</b> (설계 I272).
 *
 * <p>매물 일곱이면 한 번 계산에 49쌍입니다. 담아 두는 곳이 없어 누를 때마다 49건이
 * 나갔고, 카카오 하루 한도가 그것으로 끝났습니다([I270]).
 */
@DisplayName("자가용 길 담아 두기 (설계 I272)")
class CachingDirectionsTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, String> stored = new HashMap<>();
    private Duration storedTtl;
    private DriveRoute answer = new DriveRoute(30, 12_000,
            RoutePath.single("TRAFFIC_1", List.of(new RoutePath.Point(37.5, 127.0))));

    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 9, 5, 9, 0);
    private static final LocalDateTime SUNDAY = LocalDateTime.of(2026, 9, 6, 9, 0);

    @Test
    @DisplayName("같은 길을 두 번 물으면 한 번만 나간다")
    void asksOnce() {
        final CachingDirections directions = directions();

        final DriveRoute first = directions.findRoute(127.0, 37.5, 127.1, 37.6, SATURDAY);
        final DriveRoute second = directions.findRoute(127.0, 37.5, 127.1, 37.6, SATURDAY);

        assertThat(calls.get()).as("담아 두지 않으면 누를 때마다 49건이 나간다").isEqualTo(1);
        assertThat(second.durationMinutes()).isEqualTo(first.durationMinutes());
        assertThat(second.path().segments())
                .as("경로선까지 담아야 지도가 다시 안 부른다")
                .isNotEmpty();
        assertThat(storedTtl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("출발 시각이 다르면 다른 길이다")
    void departureTimeIsPartOfTheQuestion() {
        final CachingDirections directions = directions();

        directions.findRoute(127.0, 37.5, 127.1, 37.6, SATURDAY);
        directions.findRoute(127.0, 37.5, 127.1, 37.6, SUNDAY);

        // 토요일 09시와 일요일 09시는 다른 길이다 (설계 I196)
        assertThat(calls.get()).as("시각을 빼고 담으면 I196 이 통째로 무의미해진다").isEqualTo(2);
    }

    @Test
    @DisplayName("못 받은 것은 담지 않는다")
    void doesNotCacheAFailure() {
        answer = DriveRoute.missing();
        final CachingDirections directions = directions();

        directions.findRoute(127.0, 37.5, 127.1, 37.6, SATURDAY);
        directions.findRoute(127.0, 37.5, 127.1, 37.6, SATURDAY);

        // 담으면 <b>한 번의 실패를 하루 동안</b> 물려준다
        assertThat(calls.get()).as("실패를 담으면 하루 동안 못 고친다").isEqualTo(2);
        assertThat(stored).isEmpty();
    }

    private CachingDirections directions() {
        final KakaoDirectionsFeignClient unused = new KakaoDirectionsFeignClient() {
            @Override
            public String directions(String origin, String destination, String priority) {
                return null;
            }

            @Override
            public String futureDirections(String origin, String destination, String priority,
                                           String departureTime) {
                return null;
            }
        };
        final KakaoDirectionsAdapter kakao = new KakaoDirectionsAdapter(
                unused, "", new ObjectMapper(), new DirectionsQuota()) {
            @Override
            public DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat,
                                        LocalDateTime departAt) {
                calls.incrementAndGet();
                return answer;
            }
        };
        return new CachingDirections(kakao, new CachePort() {

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
        }, new ObjectMapper(), 24);
    }
}
