package banghak.home.halley.domain.group;

import java.time.Instant;

/** 그룹 초대 코드. */
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
