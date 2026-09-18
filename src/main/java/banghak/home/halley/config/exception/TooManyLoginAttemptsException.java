package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** 한 계정을 한 주소에서 너무 여러 번 틀렸다.  */
public class TooManyLoginAttemptsException extends BusinessException {

    public TooManyLoginAttemptsException(long minutes) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                "로그인 실패가 잦아 잠시 막습니다. " + minutes + "분 뒤에 다시 시도해 주세요");
    }
}
