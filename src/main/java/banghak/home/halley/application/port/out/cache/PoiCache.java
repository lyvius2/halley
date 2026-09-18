package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.property.NearbyFacility;

import java.util.List;

public interface PoiCache {

    List<NearbyFacility> get(long propertyId, int schemaVersion);

    void put(long propertyId, int schemaVersion, List<NearbyFacility> facilities);
}
