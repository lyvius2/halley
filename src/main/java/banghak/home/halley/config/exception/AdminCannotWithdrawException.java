package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/**
 * admin은 스스로 탈퇴할 수 없다.
 *
 * admin이 사라지면 그룹과 회원을 관리할 사람이 없어집니다. 마지막 관리자가 실수로
 * 나가면 되돌릴 방법이 앱 안에 없습니다.
 */
public class AdminCannotWithdrawException extends BusinessException {

    public AdminCannotWithdrawException() {
        super(HttpStatus.FORBIDDEN, "ADMIN_CANNOT_WITHDRAW",
                "관리자 계정은 탈퇴할 수 없습니다");
    }
}
