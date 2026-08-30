package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.MarketRateCache;
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.MarketRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * live용 Redis 구현 (설계 I81).
 *
 * <p>Redis 장애 시 조용히 건너뜁니다 — 호출 측이 기본 금리로 떨어지므로 대출 계산이 멈추지
 * 않습니다(설계 2.1.1 원칙).
 */
@Slf4j
@Component
@Profile("live")
public class RedisMarketRateCache implements MarketRateCache {

    private static final Duration TTL = Duration.ofDays(1);
    private static final String PREFIX = "marketrate:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMarketRateCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MarketRate> get(LoanProductType type) {
        try {
            final String body = redisTemplate.opsForValue().get(PREFIX + type.name());
            return body == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(body, MarketRate.class));
        } catch (RuntimeException e) {
            log.warn("Redis market rate read failed - falling back to the default rate. cause={}",
                    e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(MarketRate rate) {
        try {
            redisTemplate.opsForValue().set(
                    PREFIX + rate.type().name(), objectMapper.writeValueAsString(rate), TTL);
        } catch (RuntimeException e) {
            log.warn("Redis market rate write failed - the next call will fetch again. cause={}",
                    e.toString());
        }
    }
}
