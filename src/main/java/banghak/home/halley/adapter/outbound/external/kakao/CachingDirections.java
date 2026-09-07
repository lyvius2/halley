package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.KakaoDirectionsPort;
import banghak.home.halley.domain.itinerary.DriveRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

/** 한 번 받은 길은 다시 안 받는다. */
@Slf4j
@Component
public class CachingDirections implements KakaoDirectionsPort {

    private final KakaoDirectionsAdapter kakao;
    private final CachePort cache;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public CachingDirections(KakaoDirectionsAdapter kakao, CachePort cache, ObjectMapper objectMapper,
                             @Value("${itinerary.route-cache-hours:24}") long ttlHours) {
        this.kakao = kakao;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat,
                                LocalDateTime departAt) {
        final String key = key(fromLng, fromLat, toLng, toLat, departAt);
        final DriveRoute remembered = read(key);
        if (remembered != null) {
            return remembered;
        }
        final DriveRoute route = kakao.findRoute(fromLng, fromLat, toLng, toLat, departAt);
        if (route.isComputed()) {
            write(key, route);
        }
        return route;
    }

    private DriveRoute read(String key) {
        return cache.get(CachePort.DRIVE_ROUTE, key)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, DriveRoute.class);
                    } catch (RuntimeException e) {
                        log.warn("Cached drive route unreadable - fetching again. cause={}", e.getMessage());
                        cache.evict(CachePort.DRIVE_ROUTE, key);
                        return null;
                    }
                })
                .orElse(null);
    }

    private void write(String key, DriveRoute route) {
        try {
            cache.put(CachePort.DRIVE_ROUTE, key, objectMapper.writeValueAsString(route), ttl);
        } catch (RuntimeException e) {
            log.warn("Could not cache the drive route. cause={}", e.getMessage());
        }
    }

 /** 소수점 여섯 자리면 1m 안쪽. 같은 지점으로 봐도 된다 (과 같은 규칙). */
    private static String key(double fromLng, double fromLat, double toLng, double toLat,
                              LocalDateTime departAt) {
        return String.format("%.6f,%.6f>%.6f,%.6f@%s", fromLng, fromLat, toLng, toLat, departAt);
    }
}
