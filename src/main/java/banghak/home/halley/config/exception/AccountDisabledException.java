package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends BusinessException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "비활성화된 계정입니다");
    }
}
