package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

/** 로그인·세션 응답. */
public record AuthResponse(Long userId, String nickname, UserRole role,
                           boolean mustChangePassword, boolean profileComplete,
                           boolean profileConfirmed,
                           Integer expiresInSeconds) {
}
