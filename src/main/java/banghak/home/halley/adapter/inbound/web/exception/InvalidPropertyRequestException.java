package banghak.home.halley.adapter.inbound.web.exception;

import org.springframework.http.HttpStatus;

public class InvalidPropertyRequestException extends BusinessException {

    public InvalidPropertyRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY", message);
    }
}
