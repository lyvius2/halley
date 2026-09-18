package banghak.home.halley.domain.group;

import java.time.Instant;

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
