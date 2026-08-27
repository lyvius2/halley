package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class ConcurrentEditException extends BusinessException {

    public ConcurrentEditException() {
        super(HttpStatus.CONFLICT, "CONCURRENT_EDIT", "다른 사용자가 먼저 수정했습니다. 최신 내용을 다시 불러오세요");
    }
}
