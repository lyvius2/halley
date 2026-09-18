package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class NoGroupException extends BusinessException {

    public NoGroupException() {
        super(HttpStatus.FORBIDDEN, "NO_GROUP",
                "속한 그룹이 없습니다. 초대 코드로 그룹에 가입해 주세요");
    }
}
