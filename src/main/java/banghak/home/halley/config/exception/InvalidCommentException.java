package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidCommentException extends BusinessException {

    public InvalidCommentException(String message) {
        super(HttpStatus.BAD_REQUEST, "COMMENT_INVALID", message);
    }
}
