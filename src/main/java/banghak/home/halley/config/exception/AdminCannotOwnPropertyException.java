package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** admin은 어느 그룹에도 속하지 않으므로 매물을 등록할 수 없다. */
public class AdminCannotOwnPropertyException extends BusinessException {

    public AdminCannotOwnPropertyException() {
        super(HttpStatus.FORBIDDEN, "ADMIN_CANNOT_OWN_PROPERTY",
                "관리자는 매물을 등록할 수 없습니다. 그룹에 속한 회원 계정으로 등록해 주세요");
    }
}
