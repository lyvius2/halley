package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.PoiCache;
import banghak.home.halley.domain.property.NearbyFacility;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!live")
public class InMemoryPoiCache implements PoiCache {

    private static final Duration TTL = Duration.ofDays(30);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public List<NearbyFacility> get(long propertyId, int schemaVersion) {
        final Entry entry = store.get(key(propertyId, schemaVersion));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return List.of();
        }
        return entry.facilities();
    }

    @Override
    public void put(long propertyId, int schemaVersion, List<NearbyFacility> facilities) {
        store.put(key(propertyId, schemaVersion),
                new Entry(List.copyOf(facilities), Instant.now().plus(TTL)));
    }

    private String key(long propertyId, int schemaVersion) {
        return "poi:v" + schemaVersion + ":" + propertyId;
    }

    private record Entry(List<NearbyFacility> facilities, Instant expiresAt) {
    }
}
