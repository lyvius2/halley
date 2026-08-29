package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** 규제 파라미터·규제지역 입력이 잘못됐다 (설계 I68). */
public class InvalidRegulationException extends BusinessException {

    public InvalidRegulationException(String message) {
        super(HttpStatus.BAD_REQUEST, "REGULATION_INVALID", message);
    }
}
