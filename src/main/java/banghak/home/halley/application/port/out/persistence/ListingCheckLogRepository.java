package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.ListingCheckLog;

import java.util.List;
import java.util.Optional;

public interface ListingCheckLogRepository {

    ListingCheckLog save(ListingCheckLog log);

    Optional<ListingCheckLog> findById(Long id);

    List<ListingCheckLog> findByPropertyId(Long propertyId);

    void delete(Long id);
}
