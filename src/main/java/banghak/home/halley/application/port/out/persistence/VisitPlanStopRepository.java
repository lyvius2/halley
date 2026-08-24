package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.itinerary.VisitPlanStop;

import java.util.List;
import java.util.Optional;

public interface VisitPlanStopRepository {

    VisitPlanStop save(VisitPlanStop stop);

    Optional<VisitPlanStop> findById(Long id);

    List<VisitPlanStop> findByPlanId(Long planId);

    void delete(Long id);
}
