package banghak.home.halley.domain.property;

import java.time.Instant;

/**
 * 매물에 남기는 한 사람의 코멘트 (설계 I56).
 *
 * <p>매물 : 코멘트 = 1 : N이지만 <b>사람당 매물 하나에 한 건</b>입니다. 여러 생각이 생기면
 * 새 글을 쌓는 대신 자기 글을 고쳐 씁니다 — 둘이 쓰는 앱이라 스레드가 아니라 각자의 견해에 가깝습니다.
 */
public record PropertyComment(
        Long id,
        Long propertyId,
        Long userId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
