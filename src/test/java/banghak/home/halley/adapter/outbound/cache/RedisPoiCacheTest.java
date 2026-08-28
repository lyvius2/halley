package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.domain.property.NearbyFacility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisPoiCacheTest {

    private final Map<String, String> redis = new HashMap<>();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RedisPoiCache cache = new RedisPoiCache(stubTemplate(), objectMapper);

    @Test
    @DisplayName("POI 목록을 JSON으로 직렬화·역직렬화해 그대로 복원한다")
    void serializesRoundTrip() {
        // given
        final Instant fetchedAt = Instant.parse("2026-08-28T04:00:00Z");
        cache.put(7L, 2, List.of(
                NearbyFacility.of(7L, "STATION", "SW8", "무악재역", 300, 6, fetchedAt),
                NearbyFacility.of(7L, "GREEN", "RIVER", "홍제천", 1240, 24, fetchedAt)));

        // when
        final List<NearbyFacility> found = cache.get(7L, 2);

        // then
        assertThat(found).hasSize(2);
        assertThat(found.getFirst().name()).isEqualTo("무악재역");
        assertThat(found.getFirst().walkMinutes()).isEqualTo(6);
        assertThat(found.getFirst().fetchedAt()).isEqualTo(fetchedAt);
        assertThat(found.getLast().subCategory()).isEqualTo("RIVER");
    }

    @Test
    @DisplayName("수집 규칙 버전이 다르면 키가 달라 옛 캐시를 읽지 않는다")
    void differentSchemaVersionUsesDifferentKey() {
        // given
        cache.put(7L, 2, List.of(NearbyFacility.of(7L, "STATION", "SW8", "무악재역", 300, 6, Instant.now())));

        // then
        assertThat(cache.get(7L, 3)).isEmpty();
        assertThat(redis).containsOnlyKeys("poi:v2:7");
    }

    @Test
    @DisplayName("깨진 JSON이 들어 있어도 예외 없이 빈 목록을 반환한다")
    void corruptedValueReturnsEmpty() {
        // given
        redis.put("poi:v2:7", "{not-json");

        // then
        assertThat(cache.get(7L, 2)).isEmpty();
    }

    private StringRedisTemplate stubTemplate() {
        final StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") final ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> redis.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            redis.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        return template;
    }
}
