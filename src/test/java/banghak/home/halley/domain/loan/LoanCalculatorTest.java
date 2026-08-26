package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanCalculatorTest {

    private final LoanCalculator calculator = new LoanCalculator(new BigDecimal("0.4"), 990_000_000L);

    @Test
    @DisplayName("LTV 한도는 호가×LTV와 총액상한 중 작은 값이다")
    void ltvLimit() {
        assertThat(calculator.expectedLoanLimit(2_000_000_000L)).isEqualTo(800_000_000L);
        assertThat(calculator.expectedLoanLimit(3_000_000_000L)).isEqualTo(990_000_000L);
    }

    @Test
    @DisplayName("최종 한도는 LTV와 DSR 중 작은 값, 필요현금은 호가에서 한도를 뺀 값이다")
    void finalLimitAndRequiredCash() {
        // given — 소득 5천만, DSR 0.4 → 연 상환 2천만, 금리 4%+1% 30년 연금 환산
        final RegulationParams params = RegulationParams.defaults();
        final long asking = 800_000_000L;

        // when
        final LoanEstimateResult result = calculator.estimate(asking, 50_000_000L, 300_000_000L, false, params);

        // then
        assertThat(result.ltvLimit()).isEqualTo(320_000_000L);
        assertThat(result.finalLimit()).isLessThanOrEqualTo(result.ltvLimit());
        assertThat(result.requiredCash()).isEqualTo(asking - result.finalLimit());
        assertThat(result.monthlyPayment()).isGreaterThan(0);
    }

    @Test
    @DisplayName("생애최초는 취득세를 감면한다")
    void firstHomeDiscount() {
        // given
        final RegulationParams params = RegulationParams.defaults();

        // when
        final LoanEstimateResult normal = calculator.estimate(600_000_000L, 50_000_000L, 0L, false, params);
        final LoanEstimateResult firstHome = calculator.estimate(600_000_000L, 50_000_000L, 0L, true, params);

        // then — 취득세 1% → 6,000,000, 생애최초 50% 감면 → 3,000,000
        assertThat(normal.acquisitionTax()).isEqualTo(6_000_000L);
        assertThat(firstHome.acquisitionTax()).isEqualTo(3_000_000L);
    }
}
