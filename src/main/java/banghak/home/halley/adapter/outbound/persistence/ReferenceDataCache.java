package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Component
public class ReferenceDataCache {

    /** 사람이 손대야만 바뀌는 것들. 길게 잡아도 됩니다 — 바뀌면 지우니까요  */
    private static final Duration STATIC_TTL = Duration.ofHours(1);

    /** 운영 설정은 관리자 화면에서 자주 만집니다  */
    private static final Duration CONFIG_TTL = Duration.ofMinutes(10);

    /** 법정동코드는 사전 재적재 말고는 바뀌지 않습니다  */
    private static final Duration DICTIONARY_TTL = Duration.ofDays(1);

    /** 갈래 하나에 값이 하나뿐일 때 쓰는 키  */
    static final String WHOLE = "all";

    private final CachePort cache;
    private final ObjectMapper objectMapper;

    private final boolean enabled;

    public ReferenceDataCache(CachePort cache, ObjectMapper objectMapper,
                              @Value("${halley.cache.reference.enabled:true}") boolean enabled) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** 담아 둔 것이 있으면 그것을, 없으면 읽어서 담는다.  */
    <T> T get(String namespace, String key, TypeReference<T> type, Supplier<T> load) {
        if (!enabled) {
            return load.get();
        }
        final Optional<String> cached = read(namespace, key);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), type);
            } catch (RuntimeException e) {
                // 모양이 바뀌었을 수 있다 — 버리고 원본에서 읽는다
                log.warn("Cached reference data was unreadable - refetching from the DB. "
                        + "namespace={}, cause={}", namespace, e.getMessage());
                evict(namespace);
            }
        }
        final T fresh = load.get();
        write(namespace, key, fresh);
        return fresh;
    }

    <T> Optional<T> findOne(String namespace, String key, TypeReference<List<T>> type,
                            Supplier<Optional<T>> load) {
        final List<T> held = get(namespace, key, type,
                () -> load.get().map(List::of).orElseGet(List::of));
        return held.isEmpty() ? Optional.empty() : Optional.ofNullable(held.getFirst());
    }

    void evict(String namespace) {
        evictNow(namespace);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    evictNow(namespace);
                }
            });
        }
    }

    void evictKeys(String namespace, String... keys) {
        for (final String key : keys) {
            try {
                cache.evict(namespace, key);
            } catch (RuntimeException e) {
                log.warn("Failed to evict a reference cache entry. namespace={}, key={}, cause={}",
                        namespace, key, e.getMessage());
            }
        }
    }

    private void evictNow(String namespace) {
        try {
            cache.evictAll(namespace);
        } catch (RuntimeException e) {
            log.warn("Failed to evict reference cache - values may be stale until the TTL expires. "
                    + "namespace={}, cause={}", namespace, e.getMessage());
        }
    }

    private Optional<String> read(String namespace, String key) {
        try {
            return cache.get(namespace, key);
        } catch (RuntimeException e) {
            log.warn("Reference cache read failed - falling through to the DB. namespace={}, cause={}",
                    namespace, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String namespace, String key, Object value) {
        try {
            cache.put(namespace, key, objectMapper.writeValueAsString(value), ttlOf(namespace));
        } catch (RuntimeException e) {
            log.warn("Reference cache write failed - the next read will hit the DB again. "
                    + "namespace={}, cause={}", namespace, e.getMessage());
        }
    }

    private Duration ttlOf(String namespace) {
        if (CachePort.SYSTEM_CONFIG.equals(namespace)) {
            return CONFIG_TTL;
        }
        if (CachePort.LEGAL_DONG.equals(namespace)) {
            return DICTIONARY_TTL;
        }
        return STATIC_TTL;
    }
}
