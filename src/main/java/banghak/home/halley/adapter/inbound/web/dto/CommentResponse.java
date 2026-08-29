package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/**
 * 매물 코멘트 (설계 I56).
 *
 * @param mine 로그인한 사람이 쓴 글인지 — 화면에서 수정·삭제 버튼을 이 값으로 가른다
 */
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
