package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.Property;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository {

    Property save(Property property);

    Optional<Property> findById(Long id);

    List<Property> findAll();

    void delete(Long id);
}
