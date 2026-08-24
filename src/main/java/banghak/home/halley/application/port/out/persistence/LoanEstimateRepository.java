package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.loan.LoanEstimate;

import java.util.List;
import java.util.Optional;

public interface LoanEstimateRepository {

    LoanEstimate save(LoanEstimate estimate);

    Optional<LoanEstimate> findById(Long id);

    List<LoanEstimate> findByPropertyId(Long propertyId);

    void delete(Long id);
}
