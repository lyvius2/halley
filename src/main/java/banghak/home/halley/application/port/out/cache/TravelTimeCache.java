package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.TravelMode;

/** 대중교통 이동시간 캐시. */
public interface TravelTimeCache {

    Integer get(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat);

    void put(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat, int minutes);
}
