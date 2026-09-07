package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** 회원가입이 닫혀 있을 때. */
public class SignUpClosedException extends BusinessException {

    public SignUpClosedException() {
        super(HttpStatus.FORBIDDEN, "SIGN_UP_CLOSED",
                "지금은 회원 가입을 받지 않습니다. 관리자에게 문의해 주세요");
    }
}
