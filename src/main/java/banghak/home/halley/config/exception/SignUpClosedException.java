package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/**
 * 회원가입이 닫혀 있을 때 (설계 I95).
 *
 * <p>화면에서 링크를 숨기는 것만으로는 부족합니다 — <b>엔드포인트는 그대로 열려 있어</b>
 * 주소를 아는 사람은 그냥 가입할 수 있습니다. 막는 일은 서버가 합니다.
 */
public class SignUpClosedException extends BusinessException {

    public SignUpClosedException() {
        super(HttpStatus.FORBIDDEN, "SIGN_UP_CLOSED",
                "지금은 회원 가입을 받지 않습니다. 관리자에게 문의해 주세요");
    }
}
