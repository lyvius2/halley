package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.user.UserRole;

/**
 * 로그인·세션 응답.
 *
 * @param profileConfirmed 본인이 프로필을 확인했는지 (설계 I100). false면 앱에 들어가기 전에
 *                         확인 화면을 거친다 — 관리자가 대신 넣은 값이 맞는지 본인만 안다
 */
public record AuthResponse(Long userId, String nickname, UserRole role,
                           boolean mustChangePassword, boolean profileComplete,
                           boolean profileConfirmed,
                           Integer expiresInSeconds) {
}
