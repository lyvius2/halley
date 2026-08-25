package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class LastAdminException extends BusinessException {

    public LastAdminException(String message) {
        super(HttpStatus.BAD_REQUEST, "LAST_ADMIN", message);
    }
}
