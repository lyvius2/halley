package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidScoreException extends BusinessException {

    public InvalidScoreException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_SCORE", message);
    }
}
