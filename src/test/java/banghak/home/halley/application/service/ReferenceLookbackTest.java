package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.property.Complex;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import banghak.home.halley.adapter.outbound.cache.InMemoryCachePort;
import banghak.home.halley.application.port.out.cache.CachePort;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 국토부 실거래가 조회 범위 (설계 I98).
 *
 * <p>국토부 API는 <b>한 번에 한 달치</b>만 줍니다. 예전에는 이번 달만 불러 참고 거래가
 * 거의 늘 비어 있었습니다 — 오류가 아니라 범위가 좁았던 것입니다.
 */
@DisplayName("실거래가 조회 범위 (설계 I98)")
class ReferenceLookbackTest {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 못 찾은 것도 결과다 (설계 I219).
     *
     * <p>저장할 거래가 없다고 아무것도 남기지 않으면, 상세를 열 때마다 12개월치를
     * 다시 받아 옵니다 — <b>운영에서 실제로 그러고 있었습니다.</b>
     */
    @Test
    @DisplayName("헛걸음을 기억한다 — 상세를 다시 열어도 국토부를 또 부르지 않는다")
    void remembersTheMiss() {
        final AtomicInteger calls = new AtomicInteger();
        final MinistryReferencePort port = (lawdCd, dealYmd) -> {
            calls.incrementAndGet();
            return List.of();   // 12개월 내내 한 건도 안 맞는다
        };
        final InMemoryCachePort cache = new InMemoryCachePort();
        final ReferenceTransactionService service = serviceWith(port, cache);

        service.prefetch(11L);
        final int afterFirst = calls.get();
        service.prefetch(11L);

        assertThat(afterFirst).isGreaterThan(0);
        assertThat(calls.get()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("기억이 만료되면 다시 찾아본다 — 새 달에 거래가 생길 수 있다")
    void looksAgainOnceTheMemoryExpires() {
        final AtomicInteger calls = new AtomicInteger();
        final MinistryReferencePort port = (lawdCd, dealYmd) -> {
            calls.incrementAndGet();
            return List.of();
        };
        final InMemoryCachePort cache = new InMemoryCachePort();
        final ReferenceTransactionService service = serviceWith(port, cache);

        service.prefetch(11L);
        final int afterFirst = calls.get();
        cache.evictAll(CachePort.REFERENCE_MISS);
        service.prefetch(11L);

        assertThat(calls.get()).isEqualTo(afterFirst * 2);
    }

    @Test
    @DisplayName("24개월을 거슬러 부르고 이번 달은 건너뛴다 — 신고 지연 때문이다")
    void sweepsTwentyFourMonthsSkippingCurrent() throws Exception {
        // given — 어느 달을 물었는지 기록하는 스텁
        final List<String> asked = new CopyOnWriteArrayList<>();
        final MinistryReferencePort port = (lawdCd, dealYmd) -> {
            asked.add(dealYmd);
            return List.of();
        };
        final String thisMonth = YearMonth.now().format(YM);

        // when
        invokeFetchMonths(port, thisMonth);

        // then
        assertThat(asked).hasSize(24);
        // 이번 달은 계약이 아직 신고되지 않아 물어도 빈 응답이다
        assertThat(asked).doesNotContain(thisMonth);
        assertThat(asked).first().isEqualTo(YearMonth.now().minusMonths(1).format(YM));
        assertThat(asked).last().isEqualTo(YearMonth.now().minusMonths(24).format(YM));
    }

    @Test
    @DisplayName("달을 지정하면 그 달만 본다 — 화면에서 특정 월을 물을 때다")
    void exactMonthAsksOnce() throws Exception {
        final List<String> asked = new CopyOnWriteArrayList<>();
        final MinistryReferencePort port = (lawdCd, dealYmd) -> {
            asked.add(dealYmd);
            return List.of();
        };

        invokeFetchMonthsExact(port, "202601");

        assertThat(asked).containsExactly("202601");
    }

    /** 조회 범위 규칙만 보므로 서비스의 나머지 의존성 없이 그 메서드만 부른다. */
    private void invokeFetchMonths(MinistryReferencePort port, String baseMonth) throws Exception {
        callFetchMonths(port, baseMonth, false);
    }

    private void invokeFetchMonthsExact(MinistryReferencePort port, String baseMonth) throws Exception {
        callFetchMonths(port, baseMonth, true);
    }

    /**
     * 헛걸음 기억을 보려면 `collect` 를 통째로 지나야 한다 — 리포지터리 대역이 필요하다.
     * 이 테스트가 보는 것은 <b>국토부를 몇 번 부르는가</b> 하나다.
     */
    private ReferenceTransactionService serviceWith(MinistryReferencePort port, InMemoryCachePort cache) {
        final banghak.home.halley.adapter.outbound.persistence.PropertyRepository properties =
                org.mockito.Mockito.mock(
                        banghak.home.halley.adapter.outbound.persistence.PropertyRepository.class);
        org.mockito.Mockito.when(properties.findById(11L))
                .thenReturn(java.util.Optional.of(sampleProperty()));

        final banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository saved =
                org.mockito.Mockito.mock(
                        banghak.home.halley.adapter.outbound.persistence.ReferenceTransactionRepository.class);
        org.mockito.Mockito.when(saved.findByComplexAndArea(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(List.of());

        // 실거래는 단지에 붙는다 (설계 I266) — 이 테스트는 조회 개월 수만 본다
        final ComplexService complexes = org.mockito.Mockito.mock(ComplexService.class);
        org.mockito.Mockito.when(complexes.of(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Complex(
                        7L, "key", "석관신동아파밀리에", "서울 성북구 석관동 123",
                        null, null, Instant.now()));

        final LegalDongCodeService codes = org.mockito.Mockito.mock(LegalDongCodeService.class);
        org.mockito.Mockito.when(codes.deriveSigunguCode(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of("11290"));

        return new ReferenceTransactionService(null, properties, saved, port, codes, 12,
                new VirtualThreadGate("test", 4), complexes, cache);
    }

    private Property sampleProperty() {
        return new Property(
                11L, "석관신동아파밀리에", null,
                banghak.home.halley.domain.property.DealType.SALE, 800_000_000L, null,
                null, "서울 성북구 석관동 123", new java.math.BigDecimal("37.60"),
                new java.math.BigDecimal("127.06"), null, new java.math.BigDecimal("84.92"),
                null, 5, 15, null, null, null, 2004, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                banghak.home.halley.domain.property.SourceType.MANUAL, null, null, null, null, null,
                false, banghak.home.halley.domain.property.ListingStatus.ACTIVE, true,
                null, 0, null, 1L, "등록자", 1L, java.time.Instant.now());
    }

    private void callFetchMonths(MinistryReferencePort port, String baseMonth, boolean exact)
            throws Exception {
        final ReferenceTransactionService service = new ReferenceTransactionService(
                null, null, null, port, null, 24,
                new VirtualThreadGate("test", 4), null, null);
        final Method method = ReferenceTransactionService.class
                .getDeclaredMethod("fetchMonths", String.class, String.class, boolean.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<ReferenceTrade> result =
                (List<ReferenceTrade>) method.invoke(service, "11110", baseMonth, exact);
        assertThat(result).isNotNull();
    }
}
