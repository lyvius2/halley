package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class KakaoApiKeyMissingException extends BusinessException {

    public KakaoApiKeyMissingException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_KEY_MISSING", "카카오 REST API 키가 설정되지 않았습니다");
    }
}
