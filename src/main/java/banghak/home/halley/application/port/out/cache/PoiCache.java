package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.property.NearbyFacility;

import java.util.List;

/** 매물 주변 POI 캐시. */
public interface PoiCache {

    List<NearbyFacility> get(long propertyId, int schemaVersion);

    void put(long propertyId, int schemaVersion, List<NearbyFacility> facilities);
}
