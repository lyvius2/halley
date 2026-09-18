package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;

import java.util.List;

public interface KakaoLocalPort {

    List<GeoSearchResult> searchAddress(String query);

    List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius);

    /** 카테고리 그룹 코드가 없는 대상(공원·하천 등)은 키워드로 검색한다. */
    List<PoiResult> searchKeyword(String query, String categoryGroupCode, double x, double y, int radius);
}
