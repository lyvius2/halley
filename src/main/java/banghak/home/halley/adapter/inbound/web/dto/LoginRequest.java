package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 로그인은 이메일이 아니라 로그인 ID로 한다 (설계 I51).
 *
 * @param rememberMe 로그인 상태를 유지할지 (설계 I190). null 이면 유지하지 않는다 —
 *                   <b>기본이 유지가 되면 공용 컴퓨터에서 위험합니다</b>
 */
public record LoginRequest(String loginId, String password, Boolean rememberMe) {

    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
