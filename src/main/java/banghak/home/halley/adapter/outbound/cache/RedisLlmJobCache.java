package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.domain.llm.LlmJobState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** live용 Redis 구현. */
@Slf4j
@Component
@Profile("live")
public class RedisLlmJobCache implements LlmJobCache {

    private static final Duration RUNNING_TTL = Duration.ofMinutes(5);
    private static final Duration DONE_TTL = Duration.ofDays(1);
    private static final String PREFIX = "llmjob:";
    private static final String RUNNING = "RUNNING";
    private static final String DONE_PREFIX = "DONE:";

    private final StringRedisTemplate redisTemplate;

    public RedisLlmJobCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markRunning(String jobKey) {
        try {
            redisTemplate.opsForValue().set(PREFIX + jobKey, RUNNING, RUNNING_TTL);
        } catch (RuntimeException e) {
            log.warn("Redis LLM job cache write failed. key={}, cause={}", jobKey, e.getMessage());
        }
    }

    @Override
    public void markDone(String jobKey, String payload) {
        try {
            redisTemplate.opsForValue().set(PREFIX + jobKey, DONE_PREFIX + payload, DONE_TTL);
        } catch (RuntimeException e) {
            log.warn("Redis LLM job cache write failed. key={}, cause={}", jobKey, e.getMessage());
        }
    }

    @Override
    public Optional<LlmJobState> get(String jobKey) {
        try {
            final String value = redisTemplate.opsForValue().get(PREFIX + jobKey);
            if (value == null) {
                return Optional.empty();
            }
            if (RUNNING.equals(value)) {
                return Optional.of(LlmJobState.running());
            }
            if (value.startsWith(DONE_PREFIX)) {
                return Optional.of(LlmJobState.done(value.substring(DONE_PREFIX.length())));
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Redis LLM job cache read failed - falling back to DB. key={}, cause={}",
                    jobKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void clear(String jobKey) {
        try {
            redisTemplate.delete(PREFIX + jobKey);
        } catch (RuntimeException e) {
            log.warn("Redis LLM job cache delete failed. key={}, cause={}", jobKey, e.getMessage());
        }
    }
}
