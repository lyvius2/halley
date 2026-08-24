package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.PropertyOpinion;

import java.util.List;
import java.util.Optional;

public interface PropertyOpinionRepository {

    PropertyOpinion save(PropertyOpinion opinion);

    Optional<PropertyOpinion> findById(Long id);

    List<PropertyOpinion> findByPropertyId(Long propertyId);

    void delete(Long id);
}
