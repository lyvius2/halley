package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * 서로 무관한 외부 호출을 가상 스레드로 한꺼번에 돌린다 (설계 I108).
 *
 * <p><b>빈이 여럿입니다</b>(`VirtualThreadGateConfig`). 보정용과 전망용의 상한이 다릅니다 —
 * 하나를 나눠 쓰면 60개월을 훑는 전망 하나가 다른 매물의 보정을 전부 밀어냅니다 (설계 I129).
 *
 * <p>가상 스레드는 값이 싸서 수천 개도 만들 수 있지만, <b>그 끝에 붙은 외부 API는 그렇지 않습니다.</b>
 * 매물을 여러 건 연달아 등록하면 카카오·V-World·국토부에 동시 요청이 몰려 429가 돌아옵니다.
 * 그래서 스레드가 아니라 <b>동시 실행 수</b>를 세마포어로 묶습니다 — 상한을 넘은 작업은
 * 거절되지 않고 자리가 날 때까지 기다립니다.
 *
 * <p>세마포어는 <b>애플리케이션 전체가 하나를 나눠 씁니다.</b> 매물마다 새로 만들면
 * 동시 등록 건수만큼 상한이 곱해져 제한이 없는 것과 같아집니다.
 */
@Slf4j
public class VirtualThreadGate {

    private final Semaphore permits;
    private final int maxConcurrency;
    /** 로그에서 어느 게이트인지 가리려는 것 — 상한이 서로 다르다. */
    private final String name;

    public VirtualThreadGate(String name, int maxConcurrency) {
        this.name = name;
        this.maxConcurrency = maxConcurrency;
        // fair=true — 먼저 기다린 작업이 먼저 들어간다. 등록이 몰릴 때 특정 매물만
        // 계속 밀려 하염없이 기다리는 일을 막는다
        this.permits = new Semaphore(maxConcurrency, true);
        log.info("Virtual thread gate ready. name={}, maxConcurrency={}", name, maxConcurrency);
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    /** 지금 쓰이고 있는 자리 수 — 상한에 자주 닿는지 보려는 것. */
    public int inFlight() {
        return maxConcurrency - permits.availablePermits();
    }

    /**
     * 작업을 모두 동시에 돌리고 <b>전부 끝날 때까지 기다린다.</b>
     *
     * <p>한 작업이 터져도 나머지는 그대로 끝냅니다 — 공시가격 조회가 실패했다고
     * 실거래가까지 날아갈 이유가 없습니다. 실패한 자리에는 {@code null}이 들어갑니다.
     */
    public <T> List<T> runAll(List<Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> call(task)))
                    .toList();
            final List<T> results = new ArrayList<>(futures.size());
            for (final Future<T> future : futures) {
                results.add(join(future));
            }
            return results;
        }
    }

    private <T> T call(Callable<T> task) throws Exception {
        // 자리를 잡은 뒤에 부른다. 가상 스레드는 여기서 멈춰도 운반 스레드를 붙잡지 않는다
        permits.acquire();
        try {
            return task.call();
        } finally {
            permits.release();
        }
    }

    private <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            // 개별 실패는 호출한 쪽이 이미 단계별로 로그를 남긴다. 여기서는 자리만 비운다
            return null;
        }
    }
}
