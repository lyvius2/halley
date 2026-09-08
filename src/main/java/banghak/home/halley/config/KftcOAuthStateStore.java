package banghak.home.halley.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** OAuth 요청 위조를 막기 위한 단회성 state 값을 관리한다. */
@Component
public class KftcOAuthStateStore {
    private static final String SESSION_ATTRIBUTE = "kftc.oauth.state";
    private static final int STATE_LENGTH_BYTES = 32;

    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public KftcOAuthStateStore(KftcOpenBankingProperties properties) {
        this(properties.getOauthStateTtl(), Clock.systemUTC(), new SecureRandom());
    }

    KftcOAuthStateStore(Duration ttl, Clock clock, SecureRandom secureRandom) {
        this.ttl = ttl;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /** 현재 세션에 OAuth state를 저장하고 반환한다. */
    public String issue(HttpSession session) {
        final byte[] bytes = new byte[STATE_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        final String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, new StoredState(value, clock.instant().plus(ttl)));
        return value;
    }

    /** 전달받은 state가 현재 세션의 미사용 state와 일치하면 소비한다. */
    public boolean consume(HttpSession session, String candidate) {
        final Object attribute = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (!(attribute instanceof StoredState expected) || candidate == null
                || !clock.instant().isBefore(expected.expiresAt())) {
            return false;
        }
        return MessageDigest.isEqual(expected.value().getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private record StoredState(String value, Instant expiresAt) {
    }
}
