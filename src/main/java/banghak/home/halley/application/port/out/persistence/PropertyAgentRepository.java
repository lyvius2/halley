package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.PropertyAgent;

import java.util.List;
import java.util.Optional;

public interface PropertyAgentRepository {

    PropertyAgent save(PropertyAgent propertyAgent);

    Optional<PropertyAgent> findById(Long propertyId, Long agentId);

    List<PropertyAgent> findByPropertyId(Long propertyId);

    void delete(Long propertyId, Long agentId);
}
