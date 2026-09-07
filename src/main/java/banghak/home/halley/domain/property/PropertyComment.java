package banghak.home.halley.domain.property;

import java.time.Instant;

/** 매물에 남기는 한 사람의 코멘트. */
public record PropertyComment(
        Long id,
        Long propertyId,
        Long userId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
