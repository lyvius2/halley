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

/** 서로 무관한 외부 호출을 가상 스레드로 한꺼번에 돌린다. */
@Slf4j
public class VirtualThreadGate {

    private final Semaphore permits;
    private final int maxConcurrency;
 /** 로그에서 어느 게이트인지 가리려는 것. 상한이 서로 다르다. */
    private final String name;

    public VirtualThreadGate(String name, int maxConcurrency) {
        this.name = name;
        this.maxConcurrency = maxConcurrency;
        this.permits = new Semaphore(maxConcurrency, true);
        log.info("Virtual thread gate ready. name={}, maxConcurrency={}", name, maxConcurrency);
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

 /** 지금 쓰이고 있는 자리 수. 상한에 자주 닿는지 보려는 것. */
    public int inFlight() {
        return maxConcurrency - permits.availablePermits();
    }

 /** 작업을 모두 동시에 돌리고 전부 끝날 때까지 기다린다. */
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

 /** 맡겨 놓고 기다리지 않는다. */
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
                log.warn("Detached task failed. gate={}, cause={}", name, e.toString(), e);
            } finally {
                permits.release();
            }
        });
    }

 /** 정해진 시간 안에 끝나는 만큼만 돌린다. */
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
            return null;
        }
    }
}
