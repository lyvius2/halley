package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidGeoQueryException extends BusinessException {

    public InvalidGeoQueryException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_GEO_QUERY", "검색어를 입력해 주세요");
    }
}
