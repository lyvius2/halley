package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 5년이 <b>어떤 모양으로</b> 움직였는가 (설계 I255).
 *
 * <p>지금 LLM 에 가는 것은 점 몇 개입니다 — 4년 전 중앙값, 최근 중앙값, 전고점.
 * <b>같은 +7.6% 라도</b> 꾸준히 오른 것과 올랐다 꺾인 것은 다른 이야기인데,
 * 그 차이가 안 갑니다.
 */
@DisplayName("연도별 중앙값 (설계 I255)")
class YearlyMediansTest {

    private static final int NEWEST_YEAR = 2026;
    private final YearlyMedians medians = new YearlyMedians();

    @Test
    @DisplayName("해마다 중앙값과 건수를 적는다")
    void describesEachYear() {
        final List<MonthlyTrades> monthly = new ArrayList<>();
        monthly.add(month(2023, 5, 595_000_000L, 3));
        monthly.add(month(2024, 5, 740_000_000L, 3));
        monthly.add(month(2025, 5, 700_000_000L, 3));
        monthly.add(month(2026, 5, 640_000_000L, 3));

        assertThat(medians.describe(property(), monthly, NEWEST_YEAR))
                .isEqualTo("2023년 5.95억 (3건) · 2024년 7.4억 (3건) "
                        + "· 2025년 7억 (3건) · 2026년 6.4억 (3건)");
    }

    /**
     * <b>건수를 함께 적는 이유입니다.</b> 두 건짜리 중앙값을 추세로 읽으면 안 됩니다.
     */
    @Test
    @DisplayName("표본이 한 건뿐인 해는 뺀다")
    void dropsYearsWithASingleTrade() {
        final List<MonthlyTrades> monthly = new ArrayList<>();
        monthly.add(month(2023, 5, 595_000_000L, 3));
        monthly.add(month(2024, 5, 740_000_000L, 1));   // 한 건뿐
        monthly.add(month(2025, 5, 700_000_000L, 3));
        monthly.add(month(2026, 5, 640_000_000L, 3));

        assertThat(medians.describe(property(), monthly, NEWEST_YEAR))
                .doesNotContain("2024년")
                .contains("2023년", "2025년", "2026년");
    }

    @Test
    @DisplayName("해가 셋에 못 미치면 아무것도 안 낸다 — 없는 모양을 지어내지 않는다")
    void staysSilentWithoutEnoughYears() {
        final List<MonthlyTrades> monthly = List.of(
                month(2025, 5, 700_000_000L, 3), month(2026, 5, 640_000_000L, 3));

        assertThat(medians.describe(property(), monthly, NEWEST_YEAR)).isNull();
    }

    @Test
    @DisplayName("다른 단지·다른 평형은 세지 않는다")
    void ignoresOtherComplexesAndAreas() {
        final List<MonthlyTrades> monthly = new ArrayList<>();
        monthly.add(month(2023, 5, 595_000_000L, 3));
        monthly.add(month(2024, 5, 740_000_000L, 3));
        monthly.add(monthOf(2025, 5, "다른단지", "84.9", 900_000_000L, 3));
        monthly.add(monthOf(2026, 5, "측정단지", "114.8", 900_000_000L, 3));

        assertThat(medians.describe(property(), monthly, NEWEST_YEAR))
                .as("두 해만 남아 셋에 못 미친다")
                .isNull();
    }

    @Test
    @DisplayName("5년보다 오래된 해는 안 본다")
    void looksBackFiveYearsOnly() {
        final List<MonthlyTrades> monthly = new ArrayList<>();
        monthly.add(month(2019, 5, 400_000_000L, 3));   // 7년 전
        monthly.add(month(2023, 5, 595_000_000L, 3));
        monthly.add(month(2025, 5, 700_000_000L, 3));
        monthly.add(month(2026, 5, 640_000_000L, 3));

        assertThat(medians.describe(property(), monthly, NEWEST_YEAR))
                .doesNotContain("2019년");
    }

    private MonthlyTrades month(int year, int mon, long price, int count) {
        return monthOf(year, mon, "측정단지", "84.9", price, count);
    }

    private MonthlyTrades monthOf(int year, int mon, String name, String area,
                                  long price, int count) {
        return new MonthlyTrades("11110", YearMonth.of(year, mon), CachedDealType.TRADE,
                Collections.nCopies(count, new ReferenceTrade(
                        name, price, new BigDecimal(area), 5, LocalDate.of(year, mon, 1))),
                Instant.now());
    }

    private Property property() {
        return new Property(
                1L, "측정단지", null, DealType.SALE, 1_120_000_000L, null,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), null, 5, 15, null, null, null,
                2018, null, null, null, 300, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
