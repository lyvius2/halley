package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.CachePort;
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
 * live 용 (설계 I179).
 *
 * <p><b>Redis 가 죽어도 조용히 건너뜁니다</b>(2.1.1). 캐시가 없으면 원본에서 읽으면 됩니다 —
 * 캐시 계층 장애가 화면을 막을 이유가 없습니다.
 */
@Slf4j
@Component
@Profile("live")
public class RedisCachePort implements CachePort {

    private static final String PREFIX = "cache:";
    /** 한 번에 훑을 키 수. KEYS 는 레디스를 멈추므로 쓰지 않는다. */
    private static final int SCAN_COUNT = 200;

    private final StringRedisTemplate redisTemplate;

    public RedisCachePort(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String namespace, String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(namespace, key)));
        } catch (RuntimeException e) {
            log.warn("Redis cache read failed. namespace={}, key={}, cause={}",
                    namespace, key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String namespace, String key, String json, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(namespace, key), json, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis cache write failed. namespace={}, key={}, cause={}",
                    namespace, key, e.getMessage());
        }
    }

    @Override
    public void evict(String namespace, String key) {
        try {
            redisTemplate.delete(key(namespace, key));
        } catch (RuntimeException e) {
            log.warn("Redis cache evict failed. namespace={}, key={}, cause={}",
                    namespace, key, e.getMessage());
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
            log.warn("Redis cache evictAll failed. namespace={}, cause={}", namespace, e.getMessage());
        }
    }

    private String key(String namespace, String key) {
        return PREFIX + namespace + ":" + key;
    }
}
