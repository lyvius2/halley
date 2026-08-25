package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class DuplicateNicknameException extends BusinessException {

    public DuplicateNicknameException() {
        super(HttpStatus.CONFLICT, "NICKNAME_DUPLICATED", "이미 존재하는 닉네임입니다");
    }
}
