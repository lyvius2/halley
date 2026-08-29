package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class DuplicateLoginIdException extends BusinessException {

    public DuplicateLoginIdException() {
        super(HttpStatus.CONFLICT, "LOGIN_ID_DUPLICATED", "이미 존재하는 아이디입니다");
    }
}
