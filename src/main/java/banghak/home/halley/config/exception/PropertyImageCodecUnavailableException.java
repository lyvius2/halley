package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class PropertyImageCodecUnavailableException extends BusinessException {

    public PropertyImageCodecUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PROPERTY_IMAGE_CODEC_UNAVAILABLE",
                "HEIC 이미지 처리기가 준비되지 않았습니다. 잠시 후 다시 시도해 주세요");
    }
}
