package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class GeoSearchFailedException extends BusinessException {

    public GeoSearchFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, "GEO_SEARCH_FAILED", message);
    }
}
