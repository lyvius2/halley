package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("초당 요청 제한 (설계 I140)")
class RateGateTest {

    @Test
    @DisplayName("동시에 몰려도 초당 상한을 넘기지 않는다 — 국토부가 이걸로 429를 줬다")
    void spacesOutBurst() {
        final RateGate gate = new RateGate("test", 20); // 50ms 간격
        final VirtualThreadGate threads = new VirtualThreadGate("test", 6);

        final List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> {
                gate.acquire();
                return System.nanoTime();
            });
        }

        final long started = System.nanoTime();
        final List<Long> departures = threads.runAll(tasks).stream().sorted().toList();
        final Duration span = Duration.ofNanos(departures.getLast() - started);

        // 6건이 50ms 간격이면 마지막은 250ms 뒤에 나간다. 여유를 두고 200ms 이상만 본다
        assertThat(span).isGreaterThan(Duration.ofMillis(200));
        assertThat(departures).hasSize(6);
    }

    @Test
    @DisplayName("놀고 있었으면 기다리지 않는다 — 한 건짜리 호출까지 느려질 이유가 없다")
    void doesNotDelayWhenIdle() {
        final RateGate gate = new RateGate("test", 2); // 500ms 간격
        gate.acquire();

        final long started = System.nanoTime();
        // 간격보다 길게 쉬었으니 다음 것은 바로 나가야 한다
        sleep(600);
        gate.acquire();

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(900));
    }

    @Test
    @DisplayName("0 이하면 제한하지 않는다 — 로컬·테스트에서 꺼 둘 수 있어야 한다")
    void disabledWhenZero() {
        final RateGate gate = new RateGate("test", 0);

        final long started = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            gate.acquire();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(100));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
