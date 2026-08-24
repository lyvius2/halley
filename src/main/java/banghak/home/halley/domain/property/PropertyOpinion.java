package banghak.home.halley.domain.property;

public record PropertyOpinion(
        Long id,
        Long propertyId,
        Long userId,
        OpinionType opinionType,
        String content,
        Integer sortOrder
) {
}
