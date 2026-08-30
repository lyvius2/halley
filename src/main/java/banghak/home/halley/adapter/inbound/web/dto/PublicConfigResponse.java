package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 로그인 전에 화면이 알아야 하는 설정 (설계 I95).
 *
 * <p>회원가입 링크를 띄울지 정하려면 <b>로그인하기 전에</b> 알아야 합니다. 세션 응답에
 * 담을 수 없는 이유가 그것입니다 — 로그아웃 상태에서는 세션 조회가 401입니다.
 *
 * <p>여기에는 <b>공개해도 되는 것만</b> 담습니다. 인증 없이 누구나 읽습니다.
 */
public record PublicConfigResponse(boolean signUpOpen) {
}
