package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.PropertyDetailCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** local/개발용 (설계 I158 — TTL 24시간). live에서는 {@link RedisPropertyDetailCache}가 쓰인다. */
@Component
@Profile("!live")
public class InMemoryPropertyDetailCache implements PropertyDetailCache {

    static final Duration TTL = Duration.ofHours(24);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String namespace, long propertyId) {
        final Entry entry = store.get(key(namespace, propertyId));
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            store.remove(key(namespace, propertyId));
            return Optional.empty();
        }
        return Optional.of(entry.json());
    }

    @Override
    public void put(String namespace, long propertyId, String json) {
        store.put(key(namespace, propertyId), new Entry(json, Instant.now().plus(TTL)));
    }

    @Override
    public void evict(String namespace, long propertyId) {
        store.remove(key(namespace, propertyId));
    }

    @Override
    public void evictAll(String namespace) {
        store.keySet().removeIf(k -> k.startsWith(namespace + ":"));
    }

    private String key(String namespace, long propertyId) {
        return namespace + ":" + propertyId;
    }

    private record Entry(String json, Instant expiresAt) {
    }
}
