package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;

import java.util.List;

public interface KakaoLocalPort {

    List<GeoSearchResult> searchAddress(String query);

    List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius);
}
