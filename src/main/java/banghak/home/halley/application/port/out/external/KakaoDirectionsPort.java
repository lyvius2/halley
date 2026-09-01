package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.DriveRoute;

import java.time.LocalDateTime;

public interface KakaoDirectionsPort {

    /**
     * @param departAt 그 길을 <b>언제</b> 달리는지. <b>null 이면 지금</b> 기준 (설계 I196)
     */
    DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat,
                         LocalDateTime departAt);
}
