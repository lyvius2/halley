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

/**
 * 한 번 받은 길은 <b>다시 안 받는다</b> (설계 I272).
 *
 * <p>매물 일곱이면 한 번 계산에 <b>49쌍</b>입니다. 담아 두는 곳이 아무 데도 없어
 * 「경로 계산」을 누를 때마다 49건이 나갔고, 한도가 있는 API는 그것으로 하루가
 * 끝났습니다 — 실제로 그렇게 끝났습니다([I270]).
 *
 * <pre>
 * {"code":-10,"msg":"API limit has been exceeded."}
 * </pre>
 *
 * <h4>열쇠에 출발 시각을 넣는다</h4>
 *
 * <p>시각을 빼면 화요일 14시와 일요일 14시가 <b>같은 길</b>이 되어 [I196]이
 * 무의미해집니다. [I263]에서 요청 안의 기억(`driveMemo`)에 같은 실수를 했다가
 * 기존 시험에 걸렸습니다.
 *
 * <h4>못 받은 것은 담지 않는다</h4>
 *
 * <p>한 번의 실패를 하루 동안 물려주게 됩니다 — [I267]에서 모델 목록에 같은
 * 판단을 했습니다. 실패는 <b>다음에 다시 물어야</b> 합니다.
 *
 * <h4>여기가 캐시의 자리다</h4>
 *
 * <p>공급자 위에 둡니다. 나중에 다른 길찾기를 붙여도([I273]의 계획) 담아 두기는
 * <b>그대로</b>입니다 — 어느 쪽이 답했든 같은 좌표·같은 시각이면 같은 답입니다.
 */
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
                        // 담아 둔 모양이 바뀌었을 수 있다. 버리고 다시 받는다
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
            // 담지 못해도 길은 이미 받았다. 이번 계산은 그대로 끝낸다
            log.warn("Could not cache the drive route. cause={}", e.getMessage());
        }
    }

    /** 소수점 여섯 자리면 1m 안쪽 — 같은 지점으로 봐도 된다 ([I176]과 같은 규칙). */
    private static String key(double fromLng, double fromLat, double toLng, double toLat,
                              LocalDateTime departAt) {
        return String.format("%.6f,%.6f>%.6f,%.6f@%s", fromLng, fromLat, toLng, toLat, departAt);
    }
}
