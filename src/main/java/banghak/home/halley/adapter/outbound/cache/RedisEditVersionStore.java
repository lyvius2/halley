package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.EditVersionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("live")
public class RedisEditVersionStore implements EditVersionStore {

    private static final String PREFIX = "edit:";

    private final StringRedisTemplate redisTemplate;

    public RedisEditVersionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long current(String key) {
        try {
            final String value = redisTemplate.opsForValue().get(PREFIX + key);
            return value == null ? 1L : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("Redis 버전 조회 실패: {}", e.getMessage());
            return 1L;
        }
    }

    @Override
    public long bump(String key) {
        try {
            final Long value = redisTemplate.opsForValue().increment(PREFIX + key);
            return value == null ? 1L : value;
        } catch (RuntimeException e) {
            log.warn("Redis 버전 증가 실패: {}", e.getMessage());
            return 1L;
        }
    }
}
