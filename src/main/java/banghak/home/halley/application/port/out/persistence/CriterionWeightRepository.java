package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.scoring.CriterionWeight;

import java.util.List;
import java.util.Optional;

public interface CriterionWeightRepository {

    CriterionWeight save(CriterionWeight weight);

    Optional<CriterionWeight> findById(String criterionCode);

    List<CriterionWeight> findAll();

    void delete(String criterionCode);
}
