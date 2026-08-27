package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.property.ImageType;

public record PropertyImageResponse(
        Long id,
        Long propertyId,
        ImageType imageType,
        String storagePath
) {
}
