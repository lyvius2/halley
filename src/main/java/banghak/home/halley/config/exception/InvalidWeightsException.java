package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidWeightsException extends BusinessException {

    public InvalidWeightsException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_WEIGHTS", message);
    }
}
