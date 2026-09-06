package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InvalidPropertyImageException extends BusinessException {

    public InvalidPropertyImageException() {
        super(HttpStatus.BAD_REQUEST, "PROPERTY_IMAGE_INVALID",
                "손상되었거나 지원하지 않는 이미지입니다");
    }
}
