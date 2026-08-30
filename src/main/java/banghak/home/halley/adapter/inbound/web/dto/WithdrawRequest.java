package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 회원 탈퇴 (설계 I89 · 규칙 15·16).
 *
 * <p><b>비밀번호를 다시 받습니다.</b> 되돌릴 수 없는 행위이고, 자리를 비운 사이 남이
 * 눌러 버리는 것을 막아야 합니다.
 */
public record WithdrawRequest(String password) {
}
