package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.StartLocationCache;
import banghak.home.halley.domain.itinerary.StartLocation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** local/개발용 인메모리 캐시 (TTL 7일). live에서는 RedisStartLocationCache가 사용된다. */
@Component
@Profile("!live")
public class InMemoryStartLocationCache implements StartLocationCache {

    private static final Duration TTL = Duration.ofDays(7);

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<StartLocation> get(long userId) {
        final Entry entry = store.get(userId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.location());
    }

    @Override
    public void put(long userId, StartLocation location) {
        store.put(userId, new Entry(location, Instant.now().plus(TTL)));
    }

    private record Entry(StartLocation location, Instant expiresAt) {
    }
}
