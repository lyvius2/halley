package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.config.exception.TooManyLoginAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private final CachePort cache;

    public LoginAttemptLimiter(CachePort cache) {
        this.cache = cache;
    }

    /** 이미 한도를 넘었으면 429. 비밀번호를 보기 전에 부른다 — 맞아도 막는다.  */
    public void check(String loginId, String address) {
        if (failures(loginId, address) >= MAX_FAILURES) {
            log.warn("Login locked. loginId={}, address={}", loginId, address);
            throw new TooManyLoginAttemptsException(LOCK.toMinutes());
        }
    }

    public void recordFailure(String loginId, String address) {
        final int next = failures(loginId, address) + 1;
        // 매번 수명을 다시 준다 — 마지막 실패로부터 15분이다
        cache.put(CachePort.LOGIN_FAILURES, key(loginId, address), String.valueOf(next), LOCK);
    }

    /** 성공하면 처음부터 센다.  */
    public void reset(String loginId, String address) {
        cache.evict(CachePort.LOGIN_FAILURES, key(loginId, address));
    }

    private int failures(String loginId, String address) {
        return cache.get(CachePort.LOGIN_FAILURES, key(loginId, address))
                .map(Integer::parseInt)
                .orElse(0);
    }

    private static String key(String loginId, String address) {
        return (loginId == null ? "" : loginId.trim()) + "|" + (address == null ? "" : address);
    }
}
