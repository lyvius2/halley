package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

public record GroupInviteResponse(String code, Instant expiresAt) {
}
