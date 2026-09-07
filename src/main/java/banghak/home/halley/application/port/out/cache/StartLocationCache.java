package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.StartLocation;

import java.util.Optional;

/** 임장 출발지 캐시. */
public interface StartLocationCache {

    Optional<StartLocation> get(long userId);

    void put(long userId, StartLocation location);
}
