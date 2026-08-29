package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/**
 * 비교 우위 분석은 매물이 최소 4개 있어야 실행할 수 있다 (설계 I61).
 * 두셋으로는 '비교 우위'라는 말이 성립하지 않는다.
 */
public class InsufficientPropertiesException extends BusinessException {

    public InsufficientPropertiesException(int required, int actual) {
        super(HttpStatus.CONFLICT, "COMPARATIVE_NOT_ENOUGH_PROPERTIES",
                "비교 우위 분석은 매물이 최소 " + required + "개 있어야 합니다 (현재 " + actual + "개)");
    }
}
