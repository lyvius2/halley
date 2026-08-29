package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.TravelTimeCache;
import banghak.home.halley.domain.itinerary.TravelMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * live용 Redis 캐시 (TTL 7일, 좌표 100m 반올림 키). Redis 장애 시 조회·저장을 조용히 건너뛰어
 * 외부 인프라 장애가 임장 동선 계산을 막지 않게 한다 (설계 12.2 원칙).
 */
@Slf4j
@Component
@Profile("live")
public class RedisTravelTimeCache implements TravelTimeCache {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String PREFIX = "travel:";

    private final StringRedisTemplate redisTemplate;

    public RedisTravelTimeCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Integer get(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat) {
        try {
            final String value = redisTemplate.opsForValue().get(key(mode, fromLng, fromLat, toLng, toLat));
            return value == null ? null : Integer.valueOf(value);
        } catch (RuntimeException e) {
            log.warn("Redis travel-time cache read failed. cause={}", e.getMessage());
            return null;
        }
    }

    @Override
    public void put(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat, int minutes) {
        try {
            redisTemplate.opsForValue().set(key(mode, fromLng, fromLat, toLng, toLat), String.valueOf(minutes), TTL);
        } catch (RuntimeException e) {
            log.warn("Redis travel-time cache write failed. cause={}", e.getMessage());
        }
    }

    private String key(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat) {
        return PREFIX + mode + ":" + round(fromLng) + "," + round(fromLat) + ":" + round(toLng) + "," + round(toLat);
    }

    private String round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }
}
