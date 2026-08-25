package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationRequiredException extends BusinessException {

    public AuthenticationRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다");
    }
}
