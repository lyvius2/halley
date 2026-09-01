package banghak.home.halley.config.exception;

/**
 * ODsay 하루치를 다 썼다 (설계 I210).
 *
 * <p><b>`BusinessException` 이 아닙니다.</b> 사용자에게 보일 오류가 아니라
 * <b>다른 길로 가라는 신호</b>입니다 — 잡는 쪽이 LLM 으로 넘어갑니다.
 *
 * <p>이 예외가 있어야 하는 이유: 전에는 할당량 초과도 "경로 없음"도 똑같이
 * `TransitResult.missing()` 이었습니다. <b>둘은 다릅니다</b> — 앞은 다시 물으면
 * 될 일이고, 뒤는 물어도 소용없습니다.
 */
public class TransitQuotaExceededException extends RuntimeException {

    public TransitQuotaExceededException(String message) {
        super(message);
    }
}
