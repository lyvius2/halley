package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class DuplicateGroupNameException extends BusinessException {

    public DuplicateGroupNameException() {
        super(HttpStatus.BAD_REQUEST, "GROUP_NAME_DUPLICATED", "같은 이름의 그룹이 이미 있습니다");
    }
}
