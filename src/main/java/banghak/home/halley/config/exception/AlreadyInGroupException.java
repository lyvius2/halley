package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class AlreadyInGroupException extends BusinessException {

    public AlreadyInGroupException() {
        super(HttpStatus.BAD_REQUEST, "ALREADY_IN_GROUP", "이미 그 그룹에 속해 있습니다");
    }
}
