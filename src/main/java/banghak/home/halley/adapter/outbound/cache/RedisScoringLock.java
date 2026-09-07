package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.ScoringLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** live용 Redis 구현. */
@Slf4j
@Component
@Profile("live")
public class RedisScoringLock implements ScoringLock {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String PREFIX = "scoring:lock:";

    private final StringRedisTemplate redisTemplate;

    public RedisScoringLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(Long propertyId) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(PREFIX + propertyId, "1", TTL));
        } catch (RuntimeException e) {
            log.warn("Redis scoring lock failed - proceeding without it. propertyId={}, cause={}",
                    propertyId, e.toString());
            return true;
        }
    }

    @Override
    public void unlock(Long propertyId) {
        try {
            redisTemplate.delete(PREFIX + propertyId);
        } catch (RuntimeException e) {
            log.warn("Redis scoring unlock failed - the TTL will release it. propertyId={}, cause={}",
                    propertyId, e.toString());
        }
    }
}
