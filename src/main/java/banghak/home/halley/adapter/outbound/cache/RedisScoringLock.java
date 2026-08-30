package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.ScoringLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * live용 Redis 구현 (설계 I84).
 *
 * <p>`SET key value NX PX ttl` 한 번으로 검사와 획득을 원자적으로 합니다.
 *
 * <p>Redis 장애 시 <b>잠근 것으로 치고 통과시킵니다</b> — 채점은 멱등하게 upsert 하므로
 * 겹쳐도 마지막 값이 남을 뿐이고, 잠금이 죽었다고 채점이 멈추면 더 나쁩니다(설계 2.1.1).
 */
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
            // 못 지워도 TTL이 풀어 준다
            log.warn("Redis scoring unlock failed - the TTL will release it. propertyId={}, cause={}",
                    propertyId, e.toString());
        }
    }
}
