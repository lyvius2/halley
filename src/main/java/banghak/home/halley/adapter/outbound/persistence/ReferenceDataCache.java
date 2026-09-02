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

/**
 * 기준 정보를 담아 둔다 (설계 I239 · {@code docs/ADJUST_CACHE.md} §2.1).
 *
 * <h4>왜 저장소 안에 두는가</h4>
 *
 * <p>부르는 자리가 흩어져 있습니다 — {@code criterion}만 해도 9곳, {@code system_config}는
 * 12곳입니다. 부르는 쪽마다 캐시를 감싸면 <b>어느 하나가 빠집니다.</b> 더 나쁜 것은
 * <b>지우는 쪽</b>입니다: 저장하는 자리에서 지우는 것을 한 군데라도 빠뜨리면
 * 그때부터 옛 값으로 계산합니다. 읽는 곳과 지우는 곳을 <b>한 클래스 안에</b> 두면
 * 둘이 어긋날 자리가 없습니다.
 *
 * <p>이 프로젝트에서 반복된 실패입니다 — [I230] 규칙이 두 벌, [I232] 진단이 두 벌,
 * [I237] 스타일이 두 벌.
 *
 * <h4>N+1을 먼저 걷어냈습니다</h4>
 *
 * <p><b>캐시는 증상을 가립니다.</b> 캐시가 비는 순간(배포 직후·수명 만료·Redis 장애)
 * 원래 부하가 그대로 돌아옵니다. 그래서 [I238]에서 <b>매물마다 순위표를 읽던 것</b>을
 * 먼저 고치고, 그 위에 이것을 얹습니다.
 *
 * <h4>실패해도 값은 옵니다</h4>
 *
 * <p>캐시를 못 읽거나 못 쓰는 것은 <b>느려질 뿐</b>이지 기능이 죽을 일이 아닙니다.
 * 이 클래스를 쓸 때 운영의 Redis 는 실제로 죽어 있었습니다 — 그 상태에서도 채점과
 * 대출 계산은 돌았습니다. Redis 컨테이너를 내려도 마찬가지여야 합니다.
 *
 * <h4>DB 가 가까워져도 씁니다 (설계 I242)</h4>
 *
 * <p>PostgreSQL·Redis 가 <b>앱과 같은 EC2 안 Docker</b> 로 오면서 왕복이 20ms 에서
 * 1ms 아래로 내려갔습니다. <b>그렇다고 걷어낼 이유는 되지 않습니다</b> — 왕복이
 * 싸졌다고 해서 사람이 안 건드리는 표를 요청마다 다시 물을 이유가 생기지는 않습니다.
 *
 * <p>다만 나중에 "느린 게 캐시 탓인가"를 물을 때 가를 수 있어야 하므로
 * {@code halley.cache.reference.enabled} 로 껐다 켤 수 있습니다. <b>기본은 켜짐</b>이고,
 * 꺼도 답은 같습니다.
 */
@Slf4j
@Component
public class ReferenceDataCache {

    /** 사람이 손대야만 바뀌는 것들. 길게 잡아도 됩니다 — 바뀌면 지우니까요 */
    private static final Duration STATIC_TTL = Duration.ofHours(1);

    /** 운영 설정은 관리자 화면에서 자주 만집니다 */
    private static final Duration CONFIG_TTL = Duration.ofMinutes(10);

    /** 법정동코드는 사전 재적재 말고는 바뀌지 않습니다 */
    private static final Duration DICTIONARY_TTL = Duration.ofDays(1);

    /** 갈래 하나에 값이 하나뿐일 때 쓰는 키 */
    static final String WHOLE = "all";

    private final CachePort cache;
    private final ObjectMapper objectMapper;

    /**
     * 담아 두기를 끌 수 있게 한다 (설계 I242). <b>기본은 켜짐입니다.</b>
     *
     * <p>끄는 것은 <b>재거나 의심할 때</b>뿐입니다 — "느린 게 캐시 탓인가"는
     * 켠 채로만 재면 가를 수 없습니다({@code ADJUST_CACHE.md} §5.1).
     *
     * <p>꺼도 <b>동작은 같습니다.</b> 늘 원본에서 읽을 뿐입니다 — 무효화가 없으니
     * 낡은 값이 나올 일도 없습니다.
     *
     * <p>값은 {@code application.yaml} 에 {@code ${HALLEY_CACHE_REFERENCE_ENABLED:true}}
     * 로 <b>적혀 있습니다.</b> 여기 {@code @Value} 에만 두면 grep 으로 안 잡혀
     * <b>문서에만 있는 스위치</b>가 됩니다 — 실제로 한 번 그랬습니다.
     */
    private final boolean enabled;

    public ReferenceDataCache(CachePort cache, ObjectMapper objectMapper,
                              @Value("${halley.cache.reference.enabled:true}") boolean enabled) {
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** 담아 둔 것이 있으면 그것을, 없으면 읽어서 담는다. */
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

    /**
     * 없을 수도 있는 것을 담는다.
     *
     * <p><b>"없다"도 담습니다.</b> 없을 때만 원본까지 가면 <b>없는 것을 물을 때마다</b>
     * 캐시가 아무 일도 안 합니다 — {@code legal_dong_code} 처럼 못 찾는 경우가 잦은
     * 사전에서 특히 그렇습니다([I219]에서 실거래에 같은 처방을 했습니다).
     *
     * <p>{@code Optional} 대신 <b>빈 목록</b>으로 담습니다 — 직렬화가 단순하고,
     * "찾아봤지만 없었다"가 그대로 한 줄로 남습니다.
     */
    <T> Optional<T> findOne(String namespace, String key, TypeReference<List<T>> type,
                            Supplier<Optional<T>> load) {
        final List<T> held = get(namespace, key, type,
                () -> load.get().map(List::of).orElseGet(List::of));
        return held.isEmpty() ? Optional.empty() : Optional.ofNullable(held.getFirst());
    }

    /**
     * 바뀌었으니 지운다.
     *
     * <p><b>수명이 다하기를 기다리면 그동안 옛 값으로 계산합니다.</b> 규제 파라미터가
     * 그러면 대출 한도가 틀립니다. 키 하나가 아니라 갈래를 통째로 버리는 이유는,
     * 어느 키가 낡았는지 되짚는 것보다 단순하고 <b>기준 정보 수정은 드물기</b> 때문입니다.
     *
     * <h4>두 번 지웁니다</h4>
     *
     * <p>트랜잭션 안이라면 <b>커밋한 뒤에 한 번 더</b> 지웁니다. 쓰기 시점에만 지우면
     * 커밋 전에 끼어든 읽기가 <b>옛 값을 다시 담아</b> 버리고, 그러면 수명이 다할 때까지
     * (규제 파라미터는 한 시간) 틀린 값으로 계산합니다. 지우는 일은 싸니 두 번 합니다.
     */
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

    /**
     * 낡은 것이 <b>어느 칸인지 정확히 알 때</b>는 그 칸만 버린다.
     *
     * <p>갈래를 통째로 버리는 것({@link #evict})은 Redis 에서 {@code SCAN} 입니다.
     * 칸이 많은 갈래(법정동 사전)에서 한 건 저장할 때마다 전부 훑으면
     * <b>캐시가 오히려 짐이 됩니다.</b>
     */
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
