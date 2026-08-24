package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.scoring.CommuteResult;

import java.util.List;
import java.util.Optional;

public interface CommuteResultRepository {

    CommuteResult save(CommuteResult commuteResult);

    Optional<CommuteResult> findById(Long propertyId, Long userId);

    List<CommuteResult> findByUserId(Long userId);

    void delete(Long propertyId, Long userId);
}
