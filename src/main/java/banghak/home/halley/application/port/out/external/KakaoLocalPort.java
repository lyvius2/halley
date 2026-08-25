package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.geo.GeoSearchResult;

import java.util.List;

public interface KakaoLocalPort {

    List<GeoSearchResult> searchAddress(String query);
}
