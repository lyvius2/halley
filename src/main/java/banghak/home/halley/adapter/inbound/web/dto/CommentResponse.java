package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/** 매물 코멘트. */
public record CommentResponse(
        Long id,
        Long propertyId,
        Long userId,
        String nickname,
        String content,
        boolean mine,
        Instant createdAt,
        Instant updatedAt
) {
}
