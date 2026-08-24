package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.scoring.UserCriterionScore;

import java.util.List;
import java.util.Optional;

public interface UserCriterionScoreRepository {

    UserCriterionScore save(UserCriterionScore score);

    Optional<UserCriterionScore> findById(Long propertyId, Long userId, String criterionCode);

    List<UserCriterionScore> findByPropertyId(Long propertyId);

    List<UserCriterionScore> findByUserId(Long userId);

    void delete(Long propertyId, Long userId, String criterionCode);
}
