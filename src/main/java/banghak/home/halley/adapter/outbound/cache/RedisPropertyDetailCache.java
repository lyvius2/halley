package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.PropertyDetailCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * live용 (설계 I158 — TTL 24시간).
 *
 * <p><b>Redis가 죽어도 조용히 건너뜁니다</b>(2.1.1). 캐시가 없으면 DB에서 읽으면 됩니다 —
 * 캐시 계층 장애가 화면을 막을 이유가 없습니다.
 */
@Slf4j
@Component
@Profile("live")
public class RedisPropertyDetailCache implements PropertyDetailCache {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PREFIX = "detail:";
    /** 한 번에 훑을 키 수. KEYS 는 레디스를 멈추므로 쓰지 않는다. */
    private static final int SCAN_COUNT = 200;

    private final StringRedisTemplate redisTemplate;

    public RedisPropertyDetailCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String namespace, long propertyId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(namespace, propertyId)));
        } catch (RuntimeException e) {
            log.warn("Redis detail cache read failed. namespace={}, propertyId={}, cause={}",
                    namespace, propertyId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String namespace, long propertyId, String json) {
        try {
            redisTemplate.opsForValue().set(key(namespace, propertyId), json, TTL);
        } catch (RuntimeException e) {
            log.warn("Redis detail cache write failed. namespace={}, propertyId={}, cause={}",
                    namespace, propertyId, e.getMessage());
        }
    }

    @Override
    public void evict(String namespace, long propertyId) {
        try {
            redisTemplate.delete(key(namespace, propertyId));
        } catch (RuntimeException e) {
            log.warn("Redis detail cache evict failed. namespace={}, propertyId={}, cause={}",
                    namespace, propertyId, e.getMessage());
        }
    }

    @Override
    public void evictAll(String namespace) {
        try {
            final List<String> keys = new ArrayList<>();
            final ScanOptions options = ScanOptions.scanOptions()
                    .match(PREFIX + namespace + ":*").count(SCAN_COUNT).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("Redis detail cache evictAll failed. namespace={}, cause={}", namespace, e.getMessage());
        }
    }

    private String key(String namespace, long propertyId) {
        return PREFIX + namespace + ":" + propertyId;
    }
}
