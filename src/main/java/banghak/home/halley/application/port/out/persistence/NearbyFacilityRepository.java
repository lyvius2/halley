package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.NearbyFacility;

import java.util.List;
import java.util.Optional;

public interface NearbyFacilityRepository {

    NearbyFacility save(NearbyFacility facility);

    Optional<NearbyFacility> findById(Long id);

    List<NearbyFacility> findByPropertyId(Long propertyId);

    void delete(Long id);
}
