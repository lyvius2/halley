package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.CachePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** local/개발용 (설계 I179). live 에서는 {@link RedisCachePort} 가 쓰인다. */
@Component
@Profile("!live")
public class InMemoryCachePort implements CachePort {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String namespace, String key) {
        final Entry entry = store.get(key(namespace, key));
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            store.remove(key(namespace, key));
            return Optional.empty();
        }
        return Optional.of(entry.json());
    }

    @Override
    public void put(String namespace, String key, String json, Duration ttl) {
        store.put(key(namespace, key), new Entry(json, Instant.now().plus(ttl)));
    }

    @Override
    public void evict(String namespace, String key) {
        store.remove(key(namespace, key));
    }

    @Override
    public void evictAll(String namespace) {
        store.keySet().removeIf(k -> k.startsWith(namespace + ":"));
    }

    private String key(String namespace, String key) {
        return namespace + ":" + key;
    }

    private record Entry(String json, Instant expiresAt) {
    }
}
