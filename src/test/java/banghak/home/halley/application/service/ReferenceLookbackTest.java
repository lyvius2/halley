package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private void callFetchMonths(MinistryReferencePort port, String baseMonth, boolean exact)
            throws Exception {
        final ReferenceTransactionService service = new ReferenceTransactionService(
                null, null, null, port, null, 24);
        final Method method = ReferenceTransactionService.class
                .getDeclaredMethod("fetchMonths", String.class, String.class, boolean.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<ReferenceTrade> result =
                (List<ReferenceTrade>) method.invoke(service, "11110", baseMonth, exact);
        assertThat(result).isNotNull();
    }
}
