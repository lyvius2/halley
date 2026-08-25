package banghak.home.halley.adapter.inbound.web.exception;

import org.springframework.http.HttpStatus;

public class NotFoundListingsException extends BusinessException {

    public NotFoundListingsException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "매물을 찾을 수 없습니다");
    }
}
