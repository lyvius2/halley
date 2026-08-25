package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class NotFoundUserException extends BusinessException {

    public NotFoundUserException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다");
    }
}
