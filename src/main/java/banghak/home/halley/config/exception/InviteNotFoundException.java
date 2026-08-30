package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InviteNotFoundException extends BusinessException {

    public InviteNotFoundException() {
        super(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "초대 코드를 찾을 수 없습니다");
    }
}
