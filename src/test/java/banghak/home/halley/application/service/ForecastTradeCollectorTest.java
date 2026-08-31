package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.MonthlyTradeCacheRepository;
import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.CachedDealType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("가격 전망용 실거래 수집 (설계 I129)")
class ForecastTradeCollectorTest {

    @MockitoBean
    private MinistryReferencePort ministryReferencePort;

    @Autowired
    private MonthlyTradeCacheRepository cacheRepository;

    private ForecastTradeCollector collector;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        calls.set(0);
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return List.of(new ReferenceTrade("단지", 1_000_000_000L,
                    new BigDecimal("84.9"), 5, LocalDate.of(2026, 1, 1)));
        });
        // 60개월은 테스트를 느리게 하므로 6개월로 줄인다 — 동작은 같다
        collector = new ForecastTradeCollector(ministryReferencePort, cacheRepository,
                new VirtualThreadGate("test", 6), 6, 3, 24);
    }

    @Test
    @DisplayName("429로 실패한 달은 캐시에 담지 않는다 — 담으면 '거래 0건'으로 굳는다 (설계 I140)")
    void doesNotCacheFailedMonths() {
        // given — 전부 실패한다 (429 를 맞으면 폴백이 null 을 준다)
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return null;
        });
        // when(...) 이 기존 스텁을 한 번 부른다 — 그 몫을 빼고 센다
        calls.set(0);

        // when — 다른 테스트와 법정동을 겹치지 않게 한다. 캐시는 테스트 사이에 살아남는다
        final var first = collector.collect("41450", CachedDealType.TRADE);
        final int firstCalls = calls.get();
        calls.set(0);

        // then — 아무것도 안 담겼다
        assertThat(first).isEmpty();

        // 다시 부르면 그 달들을 전부 다시 받는다. 담겼다면 과거 달은 영영 안 받는다
        collector.collect("41450", CachedDealType.TRADE);
        assertThat(calls.get()).isEqualTo(firstCalls);
    }

    @Test
    @DisplayName("일부만 실패하면 성공한 달은 담는다 — 실패한 달만 다음에 다시 받는다")
    void cachesSuccessfulMonthsOnly() {
        // given — 짝수 번째 호출만 성공한다
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            final int n = calls.incrementAndGet();
            return n % 2 == 0 ? List.of(new ReferenceTrade("단지", 1_000_000_000L,
                    new BigDecimal("84.9"), 5, LocalDate.of(2026, 1, 1))) : null;
        });

        // when
        final var collected = collector.collect("41460", CachedDealType.TRADE);

        // then — 절반은 담겼다. 실패한 자리는 구멍으로 남고 0건으로 굳지 않았다
        assertThat(collected).hasSize(3);
        assertThat(collected).allSatisfy(m -> assertThat(m.trades()).isNotEmpty());
    }

    @Test
    @DisplayName("처음에는 모든 달을 받고, 두 번째부터는 캐시가 받는다")
    void secondCallHitsCache() {
        // when — 같은 법정동을 두 번
        collector.collect("11110", CachedDealType.TRADE);
        final int first = calls.get();
        calls.set(0);
        collector.collect("11110", CachedDealType.TRADE);

        // then — 최근 3개월은 24시간 규칙에 걸리지 않아(방금 받음) 다시 안 부른다
        assertThat(first).isEqualTo(6);
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("다른 매물이어도 같은 법정동이면 다시 부르지 않는다 — 캐시의 핵심")
    void differentPropertySameDistrictReusesCache() {
        // given — 첫 매물이 이미 받아 뒀다
        collector.collect("11140", CachedDealType.TRADE);
        calls.set(0);

        // when — 같은 법정동의 다른 매물 (수집기는 매물을 모른다. 법정동만 본다)
        final var result = collector.collect("11140", CachedDealType.TRADE);

        // then
        assertThat(calls.get()).isZero();
        assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("오래된 순으로 돌려준다 — 추세 계산이 순서를 전제한다")
    void returnsOldestFirst() {
        final var result = collector.collect("11170", CachedDealType.TRADE);

        assertThat(result).isSortedAccordingTo(
                java.util.Comparator.comparing(m -> m.dealYm()));
        assertThat(result.getLast().dealYm()).isEqualTo(YearMonth.now());
    }

    @Test
    @DisplayName("한 달이 실패해도 나머지는 저장한다")
    void keepsGoingWhenOneMonthFails() {
        // given — 특정 달만 터진다
        final String bad = YearMonth.now().minusMonths(2).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            if (bad.equals(inv.getArgument(1))) {
                throw new IllegalStateException("국토부 장애");
            }
            return List.of(new ReferenceTrade("단지", 1_000_000_000L,
                    new BigDecimal("84.9"), 5, LocalDate.of(2026, 1, 1)));
        });

        // when
        final var result = collector.collect("11215", CachedDealType.TRADE);

        // then — 5개월은 남는다. 실패한 달은 저장하지 않아 다음에 다시 받는다
        assertThat(result).hasSize(5);
        assertThat(result).noneMatch(m -> m.dealYm().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMM")).equals(bad));
    }

    @Test
    @DisplayName("법정동코드가 없으면 부르지 않는다")
    void skipsWithoutLawdCd() {
        assertThat(collector.collect(null, CachedDealType.TRADE)).isEmpty();
        assertThat(collector.collect(" ", CachedDealType.TRADE)).isEmpty();
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("거래가 없는 달도 캐시에 남는다 — 안 그러면 매번 다시 부른다 (설계 I128)")
    void cachesEmptyMonths() {
        // given
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return List.of();
        });

        // when
        collector.collect("11230", CachedDealType.TRADE);
        calls.set(0);
        collector.collect("11230", CachedDealType.TRADE);

        // then
        assertThat(calls.get()).isZero();
    }

    /** 동시 실행이 캐시 저장을 깨뜨리지 않는지 — 게이트가 여러 스레드로 돈다. */
    @Test
    @DisplayName("동시에 받아도 달마다 한 행씩만 남는다")
    void concurrentFetchDoesNotDuplicate() {
        final var seen = ConcurrentHashMap.<String>newKeySet();
        when(ministryReferencePort.fetchTrades(anyString(), anyString())).thenAnswer(inv -> {
            seen.add(inv.getArgument(1));
            return List.of();
        });

        final var result = collector.collect("11260", CachedDealType.TRADE);

        assertThat(seen).hasSize(6);
        assertThat(result).hasSize(6);
    }
}
