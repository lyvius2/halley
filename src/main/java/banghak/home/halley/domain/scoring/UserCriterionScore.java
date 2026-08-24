package banghak.home.halley.domain.scoring;

public record UserCriterionScore(
        Long propertyId,
        Long userId,
        String criterionCode,
        Integer score
) {
}
