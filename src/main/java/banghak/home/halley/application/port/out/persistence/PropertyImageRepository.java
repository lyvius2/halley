package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.PropertyImage;

import java.util.List;
import java.util.Optional;

public interface PropertyImageRepository {

    PropertyImage save(PropertyImage image);

    Optional<PropertyImage> findById(Long id);

    List<PropertyImage> findByPropertyId(Long propertyId);

    void delete(Long id);
}
