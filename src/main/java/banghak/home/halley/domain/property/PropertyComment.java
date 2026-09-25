package banghak.home.halley.domain.property;

import java.time.Instant;

public record PropertyComment(
        Long id,
        Long propertyId,
        Long userId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
