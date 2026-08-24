package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.scoring.Criterion;

import java.util.List;
import java.util.Optional;

public interface CriterionRepository {

    Criterion save(Criterion criterion);

    Optional<Criterion> findById(String code);

    List<Criterion> findAll();

    void delete(String code);
}
