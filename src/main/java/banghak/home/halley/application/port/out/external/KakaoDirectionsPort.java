package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.DriveRoute;

import java.time.LocalDateTime;

public interface KakaoDirectionsPort {

    /**
     * @param departAt 그 길을 언제 달리는지. null 이면 지금 기준
     */
    DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat,
                         LocalDateTime departAt);
}
