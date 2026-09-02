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

/**
 * 커넥션 풀이 마르는가 (설계 I243 · {@code ADJUST_CACHE.md} §5.2).
 *
 * <h4>무엇이 걱정인가</h4>
 *
 * <pre>
 * ENRICHMENT_MAX_CONCURRENCY: 400   보정이 동시에 400개
 * maximum-pool-size: 10             커넥션은 10개
 * connection-timeout: 3000          3초 안에 못 받으면 실패
 * </pre>
 *
 * <p>보정은 외부 API 대기가 대부분이라 커넥션을 잠깐만 쥡니다 — <b>아마</b> 문제
 * 없습니다. 그런데 확인한 적이 없습니다. 마르면 그 보정은 3초 뒤 실패하고,
 * 실패한 보정은 로그만 남깁니다. 사용자 눈에는 <b>"왜 얘만 채점이 안 됐지"</b> 로
 * 보입니다.
 *
 * <h4>왜 HikariCP 의 DEBUG 로그로 안 되는가</h4>
 *
 * <p>{@code com.zaxxer.hikari.pool.HikariPool} 을 DEBUG 로 올리면 풀 상태를
 * <b>30초마다</b> 찍습니다. 코드를 안 고쳐도 되니 처음엔 그것을 권했는데,
 * <b>보정 폭주는 몇 초짜리</b>입니다. 두 샘플 사이에 일어나면 통째로 안 보이고,
 * 하루 내내 {@code waiting=0} 만 보고 <b>"문제없다"고 잘못 결론</b>냅니다.
 *
 * <p>1초마다 봅니다. {@code connection-timeout} 이 3초라 <b>1초를 못 넘기는 대기는
 * 애초에 아무도 못 죽입니다</b> — 해가 될 수 있는 것은 전부 걸립니다.
 *
 * <h4>왜 {@code @Scheduled} 가 아닌가</h4>
 *
 * <p>이 앱의 스케줄러는 <b>스레드 하나</b>입니다({@code spring.task.scheduling.pool.size}
 * 기본값). 거기에 배치가 여섯 개 물려 있어, 전망 배치처럼 오래 도는 것이 잡고 있으면
 * <b>그동안 감시가 멈춥니다</b> — 하필 DB 가 바쁜 그때입니다.
 *
 * <p>그러면 30초 샘플링과 <b>같은 잘못</b>이 됩니다: 못 본 것을 없었던 것으로 읽습니다.
 * 감시는 <b>제 스레드</b>에서 돕니다. 가상 스레드 하나가 정수 셋을 읽고 자는 것이라
 * 값이 없습니다.
 *
 * <h4>조용합니다</h4>
 *
 * <p>줄 서 있는 것이 없으면 <b>아무것도 안 찍습니다.</b> 평소 로그를 늘리지 않으려는
 * 것이고([I162]), 그래서 켜 두고 잊어도 됩니다 — 찍혔다면 볼 것이 있다는 뜻입니다.
 *
 * <p>최고치는 <b>기억해 둡니다.</b> 폭주는 새벽에도 일어나고, 그때 사람이 보고
 * 있을 리 없습니다.
 */
@Slf4j
@Component
public class ConnectionPoolWatch {

    /**
     * 이만큼 줄 서면 말한다.
     *
     * <p>1~2는 정상입니다 — 커넥션이 오가는 중에 잠깐 겹치는 것뿐입니다.
     * 그것까지 찍으면 <b>진짜 문제가 소음에 묻힙니다.</b>
     */
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

    /**
     * 생성자는 <b>하나</b>입니다. 둘이면 어느 쪽으로 만들지 못 정해 기동이 막힙니다
     * (AOT 처리에서 실제로 막혔습니다) — 테스트용 통로는 {@link PoolProbe} 가 맡습니다.
     */
    public ConnectionPoolWatch(PoolProbe probe,
                               @Value("${halley.pool-watch.interval-ms:1000}") long intervalMs) {
        this.probe = probe;
        this.intervalMs = intervalMs;
    }

    /**
     * 제 스레드에서 돈다.
     *
     * <p>데몬입니다 — 이것 때문에 앱이 안 죽는 일은 없어야 합니다.
     */
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
                    // 감시가 앱을 죽이면 안 된다. 한 번 말하고 계속 돈다
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

    /**
     * 하루에 한 번, <b>볼 것이 있을 때만</b> 남긴다.
     *
     * <p>0이면 안 찍습니다. "괜찮았다"는 말을 매일 남길 이유가 없습니다 —
     * 남아 있는 줄이 곧 <b>무슨 일이 있었다</b>는 뜻이어야 합니다.
     */
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

    /**
     * Hikari 가 아니거나 아직 안 떴으면 <b>조용히 안 봅니다.</b>
     *
     * <p>감시가 안 된다고 앱이 죽을 이유는 없습니다. 다만 그때는 <b>보고 있다고
     * 착각해서도 안 되므로</b> 기동 때 한 번 말합니다.
     */
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

    /** 한 순간의 풀 상태. {@code waiting} 만이 실제로 아픈 값이다 */
    public record PoolSnapshot(int waiting, int active, int total) {
    }

    /** 풀을 들여다보는 통로. 아직 안 떴거나 Hikari 가 아니면 {@code null} 을 준다 */
    @FunctionalInterface
    public interface PoolProbe extends Supplier<PoolSnapshot> {
    }
}
