package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class SelfDeleteException extends BusinessException {

    public SelfDeleteException() {
        super(HttpStatus.BAD_REQUEST, "SELF_DELETE", "자기 자신을 삭제할 수 없습니다");
    }
}
