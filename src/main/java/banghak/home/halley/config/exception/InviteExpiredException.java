package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class InviteExpiredException extends BusinessException {

    public InviteExpiredException() {
        super(HttpStatus.BAD_REQUEST, "INVITE_EXPIRED", "만료된 초대 코드입니다. 24시간이 지나면 무효가 됩니다");
    }
}
