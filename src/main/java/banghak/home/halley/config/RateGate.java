package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.LockSupport;

/** 외부 API의 초당 요청 제한을 지킨다. */
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
            final long departAt = Math.max(now, nextAt);
            nextAt = departAt + intervalNanos;
            waitNanos = departAt - now;
        }
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
    }
}
