package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class AdminCannotWithdrawException extends BusinessException {

    public AdminCannotWithdrawException() {
        super(HttpStatus.FORBIDDEN, "ADMIN_CANNOT_WITHDRAW",
                "관리자 계정은 탈퇴할 수 없습니다");
    }
}
