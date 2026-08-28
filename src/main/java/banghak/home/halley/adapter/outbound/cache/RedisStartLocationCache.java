package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.StartLocationCache;
import banghak.home.halley.domain.itinerary.StartLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/** live용 Redis 캐시 (TTL 7일). 장애 시 조용히 건너뛴다 — 출발지는 다시 입력하면 되는 값이다. */
@Slf4j
@Component
@Profile("live")
public class RedisStartLocationCache implements StartLocationCache {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String PREFIX = "itin:start:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStartLocationCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StartLocation> get(long userId) {
        try {
            final String value = redisTemplate.opsForValue().get(PREFIX + userId);
            return value == null ? Optional.empty()
                    : Optional.of(objectMapper.readValue(value, StartLocation.class));
        } catch (RuntimeException e) {
            log.warn("Redis start-location cache read failed. userId={}, cause={}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(long userId, StartLocation location) {
        try {
            redisTemplate.opsForValue().set(PREFIX + userId, objectMapper.writeValueAsString(location), TTL);
        } catch (RuntimeException e) {
            log.warn("Redis start-location cache write failed. userId={}, cause={}", userId, e.getMessage());
        }
    }
}
