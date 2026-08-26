package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.DriveRoute;

public interface KakaoDirectionsPort {

    DriveRoute findRoute(double fromLng, double fromLat, double toLng, double toLat);
}
