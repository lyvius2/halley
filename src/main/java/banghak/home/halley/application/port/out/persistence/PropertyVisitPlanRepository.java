package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.itinerary.PropertyVisitPlan;

import java.util.List;
import java.util.Optional;

public interface PropertyVisitPlanRepository {

    PropertyVisitPlan save(PropertyVisitPlan plan);

    Optional<PropertyVisitPlan> findById(Long id);

    List<PropertyVisitPlan> findAll();

    void delete(Long id);
}
