package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanCalculatorTest {

    @Test
    @DisplayName("예상 대출한도는 호가×LTV와 총액상한 중 작은 값이다")
    void expectedLoanLimit() {
        // given
        final LoanCalculator calculator = new LoanCalculator(new BigDecimal("0.4"), 1_000_000_000L);

        // when / then
        assertThat(calculator.expectedLoanLimit(500_000_000L)).isEqualTo(200_000_000L);
        assertThat(calculator.expectedLoanLimit(3_000_000_000L)).isEqualTo(1_000_000_000L);
    }
}
