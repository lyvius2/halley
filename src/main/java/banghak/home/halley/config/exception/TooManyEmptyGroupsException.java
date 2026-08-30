package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class TooManyEmptyGroupsException extends BusinessException {

    public TooManyEmptyGroupsException() {
        super(HttpStatus.BAD_REQUEST, "TOO_MANY_EMPTY_GROUPS", "회원이 없는 그룹이 이미 2개입니다. 먼저 회원을 넣거나 빈 그룹을 정리해 주세요");
    }
}
