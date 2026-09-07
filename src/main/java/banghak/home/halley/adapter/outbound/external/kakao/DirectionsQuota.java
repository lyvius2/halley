package banghak.home.halley.adapter.outbound.external.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 길찾기 하루치를 다 썼는가. */
@Slf4j
@Component
public class DirectionsQuota {

 /** 카카오가 한도 초과를 말하는 방식. 본문에 실려 온다 */
    private static final String LIMIT_MESSAGE = "API limit has been exceeded";

    private volatile LocalDate exhaustedOn;

    public boolean exhausted() {
        return LocalDate.now().equals(exhaustedOn);
    }

 /** 이 실패가 한도 때문인가를 보고 기억한다. */
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
