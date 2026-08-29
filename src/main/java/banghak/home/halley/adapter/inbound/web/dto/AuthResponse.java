package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

public record AuthResponse(Long userId, String nickname, UserRole role,
                           boolean mustChangePassword, boolean profileComplete,
                           Integer expiresInSeconds) {
}
