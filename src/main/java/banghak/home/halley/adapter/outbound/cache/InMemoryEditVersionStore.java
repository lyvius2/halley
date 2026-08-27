package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.EditVersionStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!live")
public class InMemoryEditVersionStore implements EditVersionStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public long current(String key) {
        return store.getOrDefault(key, 1L);
    }

    @Override
    public long bump(String key) {
        return store.merge(key, 1L, Long::sum);
    }
}
