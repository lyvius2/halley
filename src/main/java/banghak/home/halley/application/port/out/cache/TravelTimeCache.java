package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.TravelMode;

/**
 * 대중교통 이동시간 캐시 (설계 10.4 — 좌표 100m 반올림 키, TTL 7일).
 * 자가용(DRIVING)은 실시간 교통을 반영해야 하므로 캐시하지 않는다.
 */
public interface TravelTimeCache {

    Integer get(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat);

    void put(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat, int minutes);
}
