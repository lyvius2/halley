package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class GroupNotFoundException extends BusinessException {

    public GroupNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다");
    }
}
