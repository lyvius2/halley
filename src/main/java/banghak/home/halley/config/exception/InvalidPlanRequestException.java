package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidPlanRequestException extends BusinessException {

    public InvalidPlanRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PLAN", message);
    }
}
