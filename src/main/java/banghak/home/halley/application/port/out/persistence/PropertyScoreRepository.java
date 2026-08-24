package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.scoring.PropertyScore;

import java.util.List;
import java.util.Optional;

public interface PropertyScoreRepository {

    PropertyScore save(PropertyScore score);

    Optional<PropertyScore> findById(Long id);

    List<PropertyScore> findByPropertyId(Long propertyId);

    void delete(Long id);
}
