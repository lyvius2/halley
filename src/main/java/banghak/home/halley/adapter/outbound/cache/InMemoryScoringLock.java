package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.ScoringLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** local용 인메모리 구현 (설계 I84). */
@Component
@Profile("!live")
public class InMemoryScoringLock implements ScoringLock {

    /** 채점 한 번은 길어야 몇 초다. 이보다 오래 걸렸으면 죽은 잠금으로 본다. */
    private static final Duration TTL = Duration.ofSeconds(30);

    private final Map<Long, Instant> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(Long propertyId) {
        final Instant now = Instant.now();
        // 만료된 잠금은 없는 것과 같다 — merge로 검사와 획득을 한 번에 한다
        return now.plus(TTL).equals(locks.merge(propertyId, now.plus(TTL),
                (existing, candidate) -> existing.isBefore(now) ? candidate : existing));
    }

    @Override
    public void unlock(Long propertyId) {
        locks.remove(propertyId);
    }
}
