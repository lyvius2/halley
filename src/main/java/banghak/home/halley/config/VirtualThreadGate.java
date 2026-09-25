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
import java.util.concurrent.TimeUnit;

@Slf4j
public class VirtualThreadGate {

    private final Semaphore permits;
    private final int maxConcurrency;
    /** 로그에서 어느 게이트인지 가리려는 것 — 상한이 서로 다르다.  */
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

    /** 지금 쓰이고 있는 자리 수 — 상한에 자주 닿는지 보려는 것.  */
    public int inFlight() {
        return maxConcurrency - permits.availablePermits();
    }

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

    public void detach(Runnable task) {
        Thread.ofVirtual().name(name + "-detached", 0).start(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                // 아무도 안 보고 있다. 여기서 안 남기면 조용히 사라진다
                log.warn("Detached task failed. gate={}, cause={}", name, e.toString(), e);
            } finally {
                permits.release();
            }
        });
    }

    public void runWithin(List<Runnable> tasks, java.time.Duration budget) {
        if (tasks.isEmpty()) {
            return;
        }
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            final List<Callable<Void>> callables = tasks.stream()
                    .map(task -> (Callable<Void>) () -> call(() -> {
                        task.run();
                        return null;
                    }))
                    .toList();
            executor.invokeAll(callables, Math.max(1, budget.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
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
