package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import java.util.List;
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
        final LoanEstimateResult result = calculator.estimate(input(asking, 50_000_000L, 300_000_000L, 0L, false), params);

        // then
        assertThat(result.ltvLimit()).isEqualTo(320_000_000L);
        assertThat(result.finalLimit()).isLessThanOrEqualTo(result.ltvLimit());
        assertThat(result.requiredCash()).isEqualTo(asking - result.finalLimit());
        assertThat(result.monthlyPayment()).isGreaterThan(0);
    }

    @Test
    @DisplayName("취득세는 구간별 세율(6억↓1%, 9억↑3%)을 적용한다")
    void acquisitionTaxBrackets() {
        // given
        final RegulationParams params = RegulationParams.defaults();

        // when / then
        assertThat(calculator.estimate(input(600_000_000L, 50_000_000L, 0L, 0L, false), params).acquisitionTax())
                .isEqualTo(6_000_000L);
        assertThat(calculator.estimate(input(1_000_000_000L, 50_000_000L, 0L, 0L, false), params).acquisitionTax())
                .isEqualTo(30_000_000L);
        final long middle = calculator.estimate(input(700_000_000L, 50_000_000L, 0L, 0L, false), params).acquisitionTax();
        assertThat(middle).isBetween(6_000_000L, 30_000_000L);
    }

    @Test
    @DisplayName("생애최초는 취득세를 감면한다")
    void firstHomeDiscount() {
        // given
        final RegulationParams params = RegulationParams.defaults();

        // when
        final LoanEstimateResult normal = calculator.estimate(input(600_000_000L, 50_000_000L, 0L, 0L, false), params);
        final LoanEstimateResult firstHome = calculator.estimate(input(600_000_000L, 50_000_000L, 0L, 0L, true), params);

        // then — 취득세 1% → 6,000,000, 생애최초 50% 감면 → 3,000,000
        assertThat(normal.acquisitionTax()).isEqualTo(6_000_000L);
        assertThat(firstHome.acquisitionTax()).isEqualTo(3_000_000L);
    }

    /** 담보가치는 호가와 같게 두고(별도 검증은 CollateralValuatorTest), MCI 가입으로 방공제를 뺀다. */
    private LoanEstimateInput input(long asking, long income, long cash, long existingLoan, boolean firstHome) {
        return new LoanEstimateInput(asking,
                CollateralValuation.of(asking, CollateralSource.ASKING_PRICE),
                income, cash, existingLoan, List.of(), firstHome, true);
    }
}
