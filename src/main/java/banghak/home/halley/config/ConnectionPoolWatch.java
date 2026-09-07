package banghak.home.halley.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.Supplier;

/** 커넥션 풀이 마르는가. */
@Slf4j
@Component
public class ConnectionPoolWatch {

 /** 이만큼 줄 서면 말한다. */
    private static final int NOISY_ABOVE = 2;

 /** 같은 말을 반복하지 않는다. 마르는 동안 1초마다 찍으면 로그가 못 쓰게 된다 */
    private static final long REPEAT_SILENCE_MS = 60_000;

    private final PoolProbe probe;
    private final long intervalMs;

    private volatile int peakWaiting;
    private volatile PoolSnapshot worst;
    private volatile long lastSpokeAt;
    private volatile boolean running;
    private Thread watcher;

 /** 생성자는 하나입니다. 둘이면 어느 쪽으로 만들지 못 정해 기동이 막힙니다 */
    public ConnectionPoolWatch(PoolProbe probe,
                               @Value("${halley.pool-watch.interval-ms:1000}") long intervalMs) {
        this.probe = probe;
        this.intervalMs = intervalMs;
    }

 /** 제 스레드에서 돈다. */
    @PostConstruct
    void start() {
        running = true;
        watcher = Thread.ofVirtual().name("pool-watch").start(() -> {
            while (running) {
                try {
                    sample();
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    log.warn("Connection pool watch stumbled - continuing. cause={}", e.toString());
                }
            }
        });
    }

    @PreDestroy
    void stop() {
        running = false;
        if (watcher != null) {
            watcher.interrupt();
        }
    }

 /** 한 번 들여다본다. 읽는 것은 정수 셋이라 값이 싸다 */
    public void sample() {
        final PoolSnapshot now = probe.get();
        if (now == null) {
            return;   // 아직 풀이 안 떴거나 Hikari 가 아니다
        }
        if (now.waiting() > peakWaiting) {
            peakWaiting = now.waiting();
            worst = now;
        }
        if (now.waiting() > NOISY_ABOVE && speakable()) {
            log.warn("Connection pool is running dry - enrichment may start failing at the "
                            + "3s timeout. waiting={}, active={}, total={}. "
                            + "Lower ENRICHMENT_MAX_CONCURRENCY before raising the pool.",
                    now.waiting(), now.active(), now.total());
        }
    }

 /** 하루에 한 번, 볼 것이 있을 때만 남긴다. */
    @Scheduled(cron = "${halley.pool-watch.report-cron:0 0 5 * * *}")
    public void reportPeak() {
        final PoolSnapshot high = worst;
        if (high == null || peakWaiting == 0) {
            return;
        }
        log.info("Connection pool high-water mark since the last report: waiting={}, active={}, "
                + "total={}", peakWaiting, high.active(), high.total());
        peakWaiting = 0;
        worst = null;
    }

 /** 지금 줄 서 있는 최고치. 테스트와 진단이 본다 */
    public int peakWaiting() {
        return peakWaiting;
    }

    private boolean speakable() {
        final long now = System.currentTimeMillis();
        if (now - lastSpokeAt < REPEAT_SILENCE_MS) {
            return false;
        }
        lastSpokeAt = now;
        return true;
    }

 /** Hikari 가 아니거나 아직 안 떴으면 조용히 안 봅니다. */
    public static PoolProbe hikariProbe(DataSource dataSource) {
        final HikariDataSource hikari = unwrap(dataSource);
        if (hikari == null) {
            log.info("Connection pool watch is off - the DataSource is not HikariCP.");
            return () -> null;
        }
        return () -> {
            final HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            return pool == null ? null : new PoolSnapshot(
                    pool.getThreadsAwaitingConnection(),
                    pool.getActiveConnections(),
                    pool.getTotalConnections());
        };
    }

    private static HikariDataSource unwrap(DataSource dataSource) {
        try {
            return dataSource.isWrapperFor(HikariDataSource.class)
                    ? dataSource.unwrap(HikariDataSource.class)
                    : null;
        } catch (SQLException e) {
            log.info("Connection pool watch is off - could not unwrap the DataSource. cause={}",
                    e.getMessage());
            return null;
        }
    }

 /** 한 순간의 풀 상태. waiting 만이 실제로 아픈 값이다 */
    public record PoolSnapshot(int waiting, int active, int total) {
    }

 /** 풀을 들여다보는 통로. 아직 안 떴거나 Hikari 가 아니면 null 을 준다 */
    @FunctionalInterface
    public interface PoolProbe extends Supplier<PoolSnapshot> {
    }
}
