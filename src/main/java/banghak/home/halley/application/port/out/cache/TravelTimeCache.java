package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.TravelMode;

public interface TravelTimeCache {

    Integer get(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat);

    void put(TravelMode mode, double fromLng, double fromLat, double toLng, double toLat, int minutes);
}
