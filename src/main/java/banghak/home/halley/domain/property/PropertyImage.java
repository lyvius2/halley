package banghak.home.halley.domain.property;

public record PropertyImage(
        Long id,
        Long propertyId,
        ImageType imageType,
        String storagePath,
        Integer sortOrder
) {
}
