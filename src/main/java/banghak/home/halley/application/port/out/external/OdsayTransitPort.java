package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;

public interface OdsayTransitPort {

    default boolean isEnabled() {
        return true;
    }

    TransitResult findTransit(double startX, double startY, double endX, double endY);

    default RoutePath findLane(String mapObj) {
        return RoutePath.empty();
    }

    default java.util.Map<String, TransitResult> findTransitBatch(java.util.Map<String, double[]> legs) {
        final java.util.Map<String, TransitResult> found = new java.util.LinkedHashMap<>();
        legs.forEach((key, c) -> found.put(key, findTransit(c[0], c[1], c[2], c[3])));
        return found;
    }
}
