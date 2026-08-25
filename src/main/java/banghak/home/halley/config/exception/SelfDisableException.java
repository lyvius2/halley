package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class SelfDisableException extends BusinessException {

    public SelfDisableException() {
        super(HttpStatus.BAD_REQUEST, "SELF_DISABLE", "자기 자신을 비활성화할 수 없습니다");
    }
}
