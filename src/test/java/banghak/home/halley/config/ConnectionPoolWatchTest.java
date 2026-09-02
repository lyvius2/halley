package banghak.home.halley.config;

import banghak.home.halley.config.ConnectionPoolWatch.PoolSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 짧은 폭주를 <b>놓치지 않는가</b> (설계 I243).
 *
 * <p>처음 권한 방법(HikariCP 의 DEBUG 로그)은 <b>30초마다</b> 찍습니다. 보정 폭주는
 * 몇 초짜리라 두 샘플 사이에 일어나면 통째로 안 보이고, 그러면 하루 내내
 * {@code waiting=0} 만 보고 <b>"문제없다"고 잘못 결론</b>냅니다.
 *
 * <p>이 프로젝트가 반복해서 겪은 실패의 모양입니다 — <b>조용히 낙관적인 방향으로
 * 틀리는 값</b> ([I219] 10건만 받고 있었다, [I230] 규칙이 두 벌이었다).
 */
@DisplayName("커넥션 풀 감시 (설계 I243)")
class ConnectionPoolWatchTest {

    @Test
    @DisplayName("한 번 스친 최고치를 기억한다")
    void remembersTheSpikeItSaw() {
        final ConnectionPoolWatch watch = watching(
                new PoolSnapshot(0, 1, 10),
                new PoolSnapshot(7, 10, 10),   // 폭주는 여기 한 번뿐이다
                new PoolSnapshot(0, 1, 10));

        watch.sample();
        watch.sample();
        watch.sample();

        assertThat(watch.peakWaiting())
                .as("스치고 지나간 것을 못 잡으면 새벽에 일어난 일은 영영 모른다")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("아무도 안 줄 서면 아무것도 기억하지 않는다")
    void quietWhenNothingIsWaiting() {
        final ConnectionPoolWatch watch = watching(
                new PoolSnapshot(0, 1, 10),
                new PoolSnapshot(0, 2, 10));

        watch.sample();
        watch.sample();

        assertThat(watch.peakWaiting()).isZero();
    }

    /**
     * 알린 뒤에는 <b>다시 0으로</b> 돌아가야 합니다 (설계 I243).
     *
     * <p>안 그러면 어제 한 번 스친 값이 <b>영원히</b> 최고치로 남아, 오늘 잠잠한지
     * 아닌지를 알 수 없습니다.
     */
    @Test
    @DisplayName("알리고 나면 다시 0부터 센다")
    void resetsAfterReporting() {
        final ConnectionPoolWatch watch = watching(new PoolSnapshot(5, 10, 10));
        watch.sample();
        assertThat(watch.peakWaiting()).isEqualTo(5);

        watch.reportPeak();

        assertThat(watch.peakWaiting())
                .as("어제 값이 남아 있으면 오늘이 잠잠한지 알 수 없다")
                .isZero();
    }

    /**
     * 풀이 아직 안 떴거나 Hikari 가 아니면 <b>조용히 안 봅니다.</b>
     * 감시가 안 된다고 앱이 죽을 이유는 없습니다.
     */
    @Test
    @DisplayName("볼 것이 없으면 터지지 않는다")
    void survivesWhenThereIsNoPool() {
        final ConnectionPoolWatch watch = new ConnectionPoolWatch(() -> null, 1000);

        watch.sample();
        watch.reportPeak();

        assertThat(watch.peakWaiting()).isZero();
    }

    /**
     * <b>제 스레드에서 돕니다</b> (설계 I243).
     *
     * <p>{@code @Scheduled} 에 태우면 스케줄러 스레드가 하나뿐이라, 배치가 오래 돌 때
     * <b>그동안 감시가 멈춥니다</b> — 하필 DB 가 바쁜 그때입니다. 그러면 30초
     * 샘플링과 같은 잘못이 됩니다: 못 본 것을 없었던 것으로 읽습니다.
     */
    @Test
    @DisplayName("스케줄러 없이도 혼자 돈다")
    void samplesOnItsOwnThread() throws InterruptedException {
        final CountDownLatch sampled = new CountDownLatch(3);
        final ConnectionPoolWatch watch = new ConnectionPoolWatch(() -> {
            sampled.countDown();
            return new PoolSnapshot(4, 10, 10);
        }, 10);

        watch.start();
        try {
            assertThat(sampled.await(2, TimeUnit.SECONDS))
                    .as("아무도 안 불러 주는데 스스로 안 보면 감시가 아니다")
                    .isTrue();
            assertThat(watch.peakWaiting()).isEqualTo(4);
        } finally {
            watch.stop();
        }
    }

    /** 내려 달라고 하면 <b>멈춰야</b> 합니다 — 안 그러면 테스트마다 스레드가 쌓입니다 */
    @Test
    @DisplayName("멈추라면 멈춘다")
    void stopsWhenAsked() throws InterruptedException {
        final AtomicInteger samples = new AtomicInteger();
        final ConnectionPoolWatch watch = new ConnectionPoolWatch(() -> {
            samples.incrementAndGet();
            return new PoolSnapshot(0, 1, 10);
        }, 10);

        watch.start();
        Thread.sleep(60);
        watch.stop();
        Thread.sleep(60);
        final int afterStop = samples.get();
        Thread.sleep(60);

        assertThat(samples.get()).isEqualTo(afterStop);
    }

    private ConnectionPoolWatch watching(PoolSnapshot... samples) {
        final Deque<PoolSnapshot> queue = new ArrayDeque<>(List.of(samples));
        return new ConnectionPoolWatch(() -> queue.isEmpty() ? null : queue.poll(), 1000);
    }
}
