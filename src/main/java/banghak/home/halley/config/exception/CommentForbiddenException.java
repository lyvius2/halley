package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** 남의 코멘트는 고치거나 지울 수 없다 (설계 I56). */
public class CommentForbiddenException extends BusinessException {

    public CommentForbiddenException() {
        super(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "본인이 남긴 코멘트만 수정·삭제할 수 있습니다");
    }
}
