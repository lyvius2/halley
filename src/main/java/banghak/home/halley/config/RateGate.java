package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.LockSupport;

/**
 * 외부 API의 <b>초당 요청 제한</b>을 지킨다 (설계 I140).
 *
 * <p>{@link VirtualThreadGate}와 재는 것이 다릅니다. 그쪽은 <b>동시에 몇 개</b>고,
 * 이쪽은 <b>1초에 몇 개</b>입니다. 동시 실행을 6으로 묶어도 각 호출이 40ms 만에 끝나면
 * <b>초당 150건</b>이 나갑니다 — 국토부가 실제로 그렇게 돌려줬습니다.
 *
 * <pre>
 * LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR
 * </pre>
 *
 * <p>기다리는 쪽을 재우는 게 아니라 <b>자리를 미리 예약합니다.</b> 호출자마다
 * 다음 출발 시각을 하나씩 받아 가므로 순서가 뒤집히지 않고, 몇 개가 몰려도
 * 간격이 그대로 유지됩니다. 가상 스레드가 여기서 멈춰도 운반 스레드는 놓아 줍니다.
 */
@Slf4j
public class RateGate {

    private final String name;
    private final long intervalNanos;
    /** 다음 호출이 출발해도 되는 시각. 예약할 때마다 한 칸씩 민다. */
    private long nextAt;

    public RateGate(String name, double permitsPerSecond) {
        this.name = name;
        this.intervalNanos = permitsPerSecond <= 0
                ? 0
                : (long) (1_000_000_000L / permitsPerSecond);
        this.nextAt = System.nanoTime();
        log.info("Rate gate ready. name={}, permitsPerSecond={}", name, permitsPerSecond);
    }

    /** 내 차례가 될 때까지 기다린다. 제한이 0 이하면 그냥 통과시킨다. */
    public void acquire() {
        if (intervalNanos == 0) {
            return;
        }
        final long waitNanos;
        synchronized (this) {
            final long now = System.nanoTime();
            // 놀고 있었으면 지금 바로. 밀려 있으면 마지막 예약 뒤로 붙는다
            final long departAt = Math.max(now, nextAt);
            nextAt = departAt + intervalNanos;
            waitNanos = departAt - now;
        }
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
    }
}
