package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** 사람당 매물 하나에 한 건 — 이미 있으면 새로 쓰지 않고 고쳐 쓴다 (설계 I56). */
public class DuplicateCommentException extends BusinessException {

    public DuplicateCommentException() {
        super(HttpStatus.CONFLICT, "COMMENT_DUPLICATED",
                "이미 이 매물에 코멘트를 남겼습니다. 기존 코멘트를 수정해 주세요");
    }
}
