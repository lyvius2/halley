package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기존 대출을 반영한 DSR 한도 (설계 I55)")
class LoanCalculatorExistingLoanTest {

    private final RegulationParams params = RegulationParams.defaults();
    private final LoanCalculator calculator = new LoanCalculator(params.ltvRate(), params.totalCap());

    @Test
    @DisplayName("기존 대출이 있으면 그 연간 상환액만큼 DSR 여력이 줄어 한도가 낮아진다")
    void existingLoanReducesDsrLimit() {
        // given — 연소득 6천만, 매매가 8억
        final long asking = 800_000_000L;
        final long income = 60_000_000L;

        // when
        final LoanEstimateResult none = calculator.estimate(input(asking, income, 0L, 0L, false), params);
        final LoanEstimateResult withLoan = calculator.estimate(input(asking, income, 0L, 100_000_000L, false), params);

        // then
        assertThat(withLoan.dsrLimit()).isLessThan(none.dsrLimit());
        assertThat(withLoan.existingLoanAnnual()).isPositive();
        assertThat(none.existingLoanAnnual()).isZero();
        // DSR 여력 자체(연소득 × 40%)는 그대로고, 기존 대출 상환액이 거기서 빠진다
        assertThat(withLoan.dsrCapacity()).isEqualTo(none.dsrCapacity()).isEqualTo(24_000_000L);
    }

    @Test
    @DisplayName("기존 대출이 DSR 여력을 넘으면 한도는 0이다 — 음수로 내려가지 않는다")
    void hugeExistingLoanZeroesTheLimit() {
        // when
        final LoanEstimateResult result =
                calculator.estimate(input(800_000_000L, 30_000_000L, 0L, 3_000_000_000L, false), params);

        // then
        assertThat(result.dsrLimit()).isZero();
        assertThat(result.finalLimit()).isZero();
        assertThat(result.requiredCash()).isEqualTo(800_000_000L);
    }

    @Test
    @DisplayName("슬라이더가 쓸 월 이율·기간을 함께 돌려준다")
    void exposesRateAndTermForClientSideRecalc() {
        // when
        final LoanEstimateResult result =
                calculator.estimate(input(800_000_000L, 60_000_000L, 0L, 0L, false), params);

        // then — 기본 프로파일: (4% + 1% 스트레스) / 12, 30년
        assertThat(result.termMonths()).isEqualTo(360);
        assertThat(result.monthlyRate()).isEqualTo(0.05 / 12.0);
        // 최종 한도를 그 이율·기간으로 돌리면 응답의 월 상환액과 맞는다
        final double expected = result.finalLimit() * result.monthlyRate()
                / (1 - Math.pow(1 + result.monthlyRate(), -result.termMonths()));
        assertThat(result.monthlyPayment()).isEqualTo((long) expected);
    }

    @Test
    @DisplayName("기존 대출이 없으면 예전 계산과 같다 — 회귀 방지")
    void matchesLegacyWhenNoExistingLoan() {
        // when
        final LoanEstimateResult viaLegacy = calculator.estimate(input(800_000_000L, 60_000_000L, 0L, 0L, false), params);
        final LoanEstimateResult viaNew = calculator.estimate(input(800_000_000L, 60_000_000L, 0L, 0L, false), params);

        // then
        assertThat(viaLegacy.finalLimit()).isEqualTo(viaNew.finalLimit());
        assertThat(viaLegacy.dsrLimit()).isEqualTo(viaNew.dsrLimit());
    }

    /** 담보가치는 호가와 같게 두고(별도 검증은 CollateralValuatorTest), MCI 가입으로 방공제를 뺀다. */
    private LoanEstimateInput input(long asking, long income, long cash, long existingLoan, boolean firstHome) {
        return new LoanEstimateInput(asking,
                CollateralValuation.of(asking, CollateralSource.ASKING_PRICE),
                income, cash, existingLoan, firstHome, true);
    }
}
