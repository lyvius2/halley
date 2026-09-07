package banghak.home.halley.adapter.outbound.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 길찾기 하루치를 다 썼는가.
 *
 * 운영에서 이것이 났습니다.
 *
 *
 * {"code":-10,"msg":"API limit has been exceeded."}
 *   → CircuitBreaker 'kakao-directions' is OPEN
 *   → 모든 구간이 fallback
 *
 *
 * 한 번 한도를 보면 그날은 더 부르지 않습니다. 안 그러면 「경로 계산」을
 * 누를 때마다 49번을 더 던져 얻는 것 없이 차단기만 여닫습니다.
 *
 * 날짜로 둡니다. 한도가 하루 단위라 자정을 넘기면 저절로 풀립니다 —
 * 타이머를 두면 서버를 다시 띄울 때 잃습니다.이 ODsay 에 쓴 것과 같은 모양입니다.
 */
@Slf4j
@Component
public class DirectionsQuota {

    /** 카카오가 한도 초과를 말하는 방식. 본문에 실려 온다 */
    private static final String LIMIT_MESSAGE = "API limit has been exceeded";

    private volatile LocalDate exhaustedOn;

    public boolean exhausted() {
        return LocalDate.now().equals(exhaustedOn);
    }

    /**
     * 이 실패가 한도 때문인가를 보고 기억한다.
     *
     * 차단기가 열린 뒤의 실패는 CallNotPermittedException 이라
     * 한도라는 말이 없습니다 — 그건 결과이지 원인이 아닙니다.
     * 그래서 원인이 실제로 한도일 때만 표시합니다.
     */
    public void recordIfExhausted(Throwable cause) {
        if (cause == null || !mentionsLimit(cause)) {
            return;
        }
        if (exhausted()) {
            return;
        }
        exhaustedOn = LocalDate.now();
        log.warn("Kakao Directions daily limit reached - not calling again today.");
    }

    private static boolean mentionsLimit(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            final String message = t.getMessage();
            if (message != null && message.contains(LIMIT_MESSAGE)) {
                return true;
            }
        }
        return false;
    }
}
