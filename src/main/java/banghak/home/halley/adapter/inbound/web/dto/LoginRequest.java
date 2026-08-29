package banghak.home.halley.adapter.inbound.web.dto;

/** 로그인은 이메일이 아니라 로그인 ID로 한다 (설계 I51). */
public record LoginRequest(String loginId, String password) {
}
