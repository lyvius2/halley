package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/**
 * admin은 어느 그룹에도 속하지 않으므로 매물을 등록할 수 없다 (설계 I87 · 규칙 5).
 *
 * <p>매물은 반드시 그룹에 딸립니다. admin이 등록하면 <b>어느 그룹에도 속하지 않는 매물</b>이
 * 생기는데, 그건 아무도 볼 수 없고 그룹이 삭제돼도 남습니다.
 */
public class AdminCannotOwnPropertyException extends BusinessException {

    public AdminCannotOwnPropertyException() {
        super(HttpStatus.FORBIDDEN, "ADMIN_CANNOT_OWN_PROPERTY",
                "관리자는 매물을 등록할 수 없습니다. 그룹에 속한 회원 계정으로 등록해 주세요");
    }
}
