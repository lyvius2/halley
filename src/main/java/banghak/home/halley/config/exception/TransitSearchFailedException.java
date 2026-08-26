package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class TransitSearchFailedException extends BusinessException {

    public TransitSearchFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, "TRANSIT_SEARCH_FAILED", message);
    }
}
