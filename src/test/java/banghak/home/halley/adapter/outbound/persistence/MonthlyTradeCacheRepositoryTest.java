package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("법정동·월별 실거래 캐시 (설계 I128)")
class MonthlyTradeCacheRepositoryTest {

    @Autowired
    private MonthlyTradeCacheRepository repository;

    @Test
    @DisplayName("거래를 통째로 넣고 그대로 돌려받는다")
    void roundTrips() {
        // given
        final var trades = List.of(
                new ReferenceTrade("측정단지", 1_140_000_000L, new BigDecimal("84.93"), 7,
                        LocalDate.of(2026, 3, 14)),
                new ReferenceTrade("측정단지", 1_210_000_000L, new BigDecimal("84.93"), 12,
                        LocalDate.of(2026, 3, 2)));

        // when
        repository.upsert(new MonthlyTrades("41597", YearMonth.of(2026, 3), trades, Instant.now()));

        // then
        final var found = repository.find("41597", YearMonth.of(2026, 3)).orElseThrow();
        assertThat(found.count()).isEqualTo(2);
        assertThat(found.trades().getFirst().dealAmount()).isEqualTo(1_140_000_000L);
        // 소수점이 살아 있어야 면적대 판정이 맞는다
        assertThat(found.trades().getFirst().areaM2()).isEqualByComparingTo("84.93");
        assertThat(found.trades().getFirst().contractDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(found.trades().getFirst().floorNo()).isEqualTo(7);
    }

    @Test
    @DisplayName("같은 법정동·달을 다시 넣으면 갈아 끼운다 — 행이 늘지 않는다")
    void upsertReplaces() {
        // given
        repository.upsert(new MonthlyTrades("11710", YearMonth.of(2026, 1),
                List.of(trade(900_000_000L)), Instant.now()));

        // when — 국토부가 뒤늦게 신고분을 더 준 상황
        repository.upsert(new MonthlyTrades("11710", YearMonth.of(2026, 1),
                List.of(trade(900_000_000L), trade(950_000_000L)), Instant.now()));

        // then
        assertThat(repository.find("11710", YearMonth.of(2026, 1)).orElseThrow().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 달을 한 번에 읽는다 — 60개월을 달마다 물으면 캐시를 두는 뜻이 없다")
    void findsManyMonthsAtOnce() {
        // given
        for (int m = 1; m <= 5; m++) {
            repository.upsert(new MonthlyTrades("28237", YearMonth.of(2025, m),
                    List.of(trade(800_000_000L + m)), Instant.now()));
        }

        // when — 없는 달을 섞어 물어도 있는 것만 온다
        final var found = repository.findAll("28237", List.of(
                YearMonth.of(2025, 2), YearMonth.of(2025, 4), YearMonth.of(2025, 11)));

        // then
        assertThat(found).containsOnlyKeys(YearMonth.of(2025, 2), YearMonth.of(2025, 4));
    }

    @Test
    @DisplayName("거래가 없는 달도 저장한다 — '아직 안 받은 달'과 '받았는데 없는 달'은 다르다")
    void storesEmptyMonths() {
        // when
        repository.upsert(new MonthlyTrades("50110", YearMonth.of(2024, 7), List.of(), Instant.now()));

        // then — 비었다고 없는 것으로 치면 매번 다시 부른다
        assertThat(repository.find("50110", YearMonth.of(2024, 7))).isPresent();
        assertThat(repository.find("50110", YearMonth.of(2024, 7)).orElseThrow().count()).isZero();
    }

    @Test
    @DisplayName("안 받은 달은 비어 있다")
    void missingMonthIsEmpty() {
        assertThat(repository.find("99999", YearMonth.of(2020, 1))).isEmpty();
        assertThat(repository.findAll("99999", List.of())).isEmpty();
        assertThat(repository.findAll(null, List.of(YearMonth.of(2020, 1)))).isEmpty();
    }

    private ReferenceTrade trade(long amount) {
        return new ReferenceTrade("단지", amount, new BigDecimal("59.9"), 3, LocalDate.of(2025, 6, 1));
    }
}
