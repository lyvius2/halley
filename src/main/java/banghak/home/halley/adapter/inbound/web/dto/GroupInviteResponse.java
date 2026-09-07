package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/** 발급된 초대 코드. */
public record GroupInviteResponse(String code, Instant expiresAt) {
}
