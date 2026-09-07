package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.property.NearbyFacility;

import java.util.List;

/**
 * 매물 주변 POI 캐시.
 *
 * `schemaVersion`은 수집 규칙의 버전이며 캐시 키에 포함된다. 수집 카테고리·키워드·분류 규칙이
 * 바뀌면 버전만 올려 배포하면 옛 캐시가 즉시 무시되고 전량 재수집된다(수동 삭제 불필요).
 * 파서의 `parser_version`과 같은 방식이다.
 */
public interface PoiCache {

    List<NearbyFacility> get(long propertyId, int schemaVersion);

    void put(long propertyId, int schemaVersion, List<NearbyFacility> facilities);
}
