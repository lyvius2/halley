package banghak.home.halley.adapter.inbound.web.dto;

/** 스스로 하는 회원가입. */
public record SignUpRequest(String loginId, String nickname, String password) {
}
