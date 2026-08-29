package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.PoiCache;
import banghak.home.halley.domain.property.NearbyFacility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * live용 Redis POI 캐시 (설계 2.1.1 — TTL 30일). Redis 장애 시 조회·저장을 조용히 건너뛰어
 * 캐시 계층 장애가 채점을 막지 않게 한다(외부 API 재호출로 흡수 — 설계 2.1.1).
 */
@Slf4j
@Component
@Profile("live")
public class RedisPoiCache implements PoiCache {

    private static final Duration TTL = Duration.ofDays(30);
    private static final String PREFIX = "poi:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPoiCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NearbyFacility> get(long propertyId, int schemaVersion) {
        try {
            final String value = redisTemplate.opsForValue().get(key(propertyId, schemaVersion));
            if (value == null) {
                return List.of();
            }
            return objectMapper.readValue(value, new TypeReference<List<NearbyFacility>>() {
            });
        } catch (RuntimeException e) {
            log.warn("Redis POI cache read failed. propertyId={}, cause={}", propertyId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void put(long propertyId, int schemaVersion, List<NearbyFacility> facilities) {
        try {
            redisTemplate.opsForValue().set(
                    key(propertyId, schemaVersion), objectMapper.writeValueAsString(facilities), TTL);
        } catch (RuntimeException e) {
            log.warn("Redis POI cache write failed. propertyId={}, cause={}", propertyId, e.getMessage());
        }
    }

    private String key(long propertyId, int schemaVersion) {
        return PREFIX + "v" + schemaVersion + ":" + propertyId;
    }
}
