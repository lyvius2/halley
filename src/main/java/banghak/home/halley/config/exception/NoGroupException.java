package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/**
 * 그룹에 속하지 않은 회원이 매물을 다루려 할 때 (설계 I87).
 *
 * <p>admin이라 그룹이 없는 것과 <b>다른 상황입니다.</b> 회원은 반드시 어느 그룹엔가 속해야
 * 하므로 이건 정상 상태가 아닙니다 — 메시지를 뭉뚱그리면 왜 막혔는지 알 수 없습니다.
 */
public class NoGroupException extends BusinessException {

    public NoGroupException() {
        super(HttpStatus.FORBIDDEN, "NO_GROUP",
                "속한 그룹이 없습니다. 초대 코드로 그룹에 가입해 주세요");
    }
}
