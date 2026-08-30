package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

/**
 * 발급된 초대 코드 (설계 I89).
 *
 * <p>전달은 앱이 하지 않습니다(규칙 10) — 화면에 띄우면 사람이 알아서 전합니다.
 */
public record GroupInviteResponse(String code, Instant expiresAt) {
}
