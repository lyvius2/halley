package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.config.exception.TooManyLoginAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그인 실패를 세어 무차별 대입을 막는다 (설계 I298).
 *
 * <p>계정과 주소를 <b>함께</b> 묶습니다. 계정만 세면 남이 내 계정을 잠글 수 있고,
 * 주소만 세면 한 주소에서 여러 계정을 돌려 가며 시도할 수 있습니다.
 *
 * <p>캐시에 둡니다 — 세션·rate limit 은 Redis 의 몫이고(AGENTS.md), 수명이 곧 잠금 시간이라
 * 풀어 주는 코드가 따로 필요 없습니다. Redis 가 죽어 있으면 {@code CachePort} 가 빈 값을
 * 주므로 <b>제한이 풀립니다</b> — 로그인 자체가 막히는 것보다 낫습니다.
 */
@Slf4j
@Service
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private final CachePort cache;

    public LoginAttemptLimiter(CachePort cache) {
        this.cache = cache;
    }

    /** 이미 한도를 넘었으면 429. 비밀번호를 보기 전에 부른다 — 맞아도 막는다. */
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

    /** 성공하면 처음부터 센다. */
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
