package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException() {
        super(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "이미 존재하는 이메일입니다");
    }
}
