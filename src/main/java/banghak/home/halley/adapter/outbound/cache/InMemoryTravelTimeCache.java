package banghak.home.halley.adapter.outbound.cache;

import banghak.home.halley.application.port.out.cache.TravelTimeCache;
import banghak.home.halley.domain.itinerary.TravelMode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * local/개발용 인메모리 캐시 (TTL 7일). live에서는 RedisTravelTimeCache가 사용된다.
 */
@Component
@Profile("!live")
public class InMemoryTravelTimeCache implements TravelTimeCache {

    private static final Duration TTL = Duration.ofDays(7);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Integer get(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat) {
        final Entry entry = store.get(key(mode, fromLng, fromLat, toLng, toLat));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return entry.minutes();
    }

    @Override
    public void put(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat, int minutes) {
        store.put(key(mode, fromLng, fromLat, toLng, toLat),
                new Entry(minutes, Instant.now().plus(TTL)));
    }

    private String key(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat) {
        return mode + ":" + round(fromLng) + "," + round(fromLat) + ":" + round(toLng) + "," + round(toLat);
    }

    private String round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private record Entry(int minutes, Instant expiresAt) {
    }
}
