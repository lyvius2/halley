package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 스스로 하는 회원가입.
 *
 * 그룹은 받지 않습니다 — 가입과 동시에 새 그룹이 자동으로 생깁니다. 가입하는
 * 순간에는 그룹이 무엇인지도 모르는 상태라 이름을 물어도 의미 없는 값이 들어갑니다.
 */
public record SignUpRequest(String loginId, String nickname, String password) {
}
