package banghak.home.halley.domain.group;

import java.time.Instant;

/**
 * 그룹 초대 코드 (설계 I89 · 규칙 8~11).
 *
 * <p>코드를 <b>기본키</b>로 둡니다. 살아 있는 코드끼리 겹치지 않아야 하는데(규칙 8),
 * 기본키면 그 보장이 데이터베이스 차원에서 나옵니다 — 코드 생성 쪽에서 확인하면 두 사람이
 * 동시에 같은 코드를 뽑는 경우를 놓칩니다.
 *
 * <p>전달 방법은 앱이 제공하지 않습니다(규칙 10). 화면에 띄우면 사람이 알아서 전합니다.
 */
public record GroupInvite(
        String code,
        Long groupId,
        Long createdBy,
        Instant createdAt,
        Instant expiresAt
) {

    public boolean isExpired(Instant now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }
}
