package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.StartLocation;

import java.util.Optional;

public interface StartLocationCache {

    Optional<StartLocation> get(long userId);

    void put(long userId, StartLocation location);
}
