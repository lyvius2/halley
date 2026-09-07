package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;

public interface OdsayTransitPort {

 /** 설정이 갖춰져 실제로 호출할 수 있는지. */
    default boolean isEnabled() {
        return true;
    }

    TransitResult findTransit(double startX, double startY, double endX, double endY);

 /** 그 경로의 실제 선을 받아 온다. */
    default RoutePath findLane(String mapObj) {
        return RoutePath.empty();
    }

 /** 여러 구간을 한꺼번에. */
    default java.util.Map<String, TransitResult> findTransitBatch(java.util.Map<String, double[]> legs) {
        final java.util.Map<String, TransitResult> found = new java.util.LinkedHashMap<>();
        legs.forEach((key, c) -> found.put(key, findTransit(c[0], c[1], c[2], c[3])));
        return found;
    }
}
