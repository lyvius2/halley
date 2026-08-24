package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.property.ReferenceTransaction;

import java.util.List;
import java.util.Optional;

public interface ReferenceTransactionRepository {

    ReferenceTransaction save(ReferenceTransaction transaction);

    Optional<ReferenceTransaction> findById(Long id);

    List<ReferenceTransaction> findByPropertyId(Long propertyId);

    void delete(Long id);
}
