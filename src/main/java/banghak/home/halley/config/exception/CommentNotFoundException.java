package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

public class CommentNotFoundException extends BusinessException {

    public CommentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "코멘트를 찾을 수 없습니다");
    }
}
