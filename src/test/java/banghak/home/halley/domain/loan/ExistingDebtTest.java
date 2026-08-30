package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기존 부채 종류별 DSR (설계 I92 · 로드맵 5단계)")
class ExistingDebtTest {

    private static final double RATE = 0.05;

    @Test
    @DisplayName("같은 1억이라도 신용대출은 주담대보다 훨씬 무겁게 잡힌다")
    void creditLoanWeighsMoreThanMortgage() {
        // given — 같은 금액, 다른 종류
        final long amount = 100_000_000L;

        // when
        final long mortgage = new ExistingDebt(DebtType.MORTGAGE, amount).annualPayment(RATE);
        final long credit = new ExistingDebt(DebtType.CREDIT, amount).annualPayment(RATE);

        // then — 30년 vs 5년. 이걸 구분하지 않으면 한도가 실제보다 크게 나온다
        assertThat(credit).isGreaterThan(mortgage * 3);
    }

    @Test
    @DisplayName("전세자금대출은 원금을 빼고 이자만 센다")
    void jeonseCountsInterestOnly() {
        // when
        final long jeonse = new ExistingDebt(DebtType.JEONSE, 200_000_000L).annualPayment(RATE);

        // then — 2억 × 5% = 1,000만원
        assertThat(jeonse).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("종류별 입력이 없으면 옛 단일 금액을 주담대로 본다 — 부채가 사라지면 한도가 부풀려진다")
    void fallsBackToLegacyAmount() {
        // given — 종류를 아직 입력하지 않은 사용자
        final LoanEstimateInput input = new LoanEstimateInput(
                1_000_000_000L, null, 60_000_000L, 100_000_000L,
                100_000_000L, List.of(), false, false, RateType.VARIABLE);

        // when
        final long annual = input.existingDebtAnnualPayment(RATE);

        // then
        assertThat(annual)
                .isEqualTo(new ExistingDebt(DebtType.MORTGAGE, 100_000_000L).annualPayment(RATE))
                .isPositive();
    }

    @Test
    @DisplayName("종류별 입력이 있으면 그것만 합산한다 — 옛 금액과 이중으로 세지 않는다")
    void typedDebtsReplaceLegacyAmount() {
        // given — 옛 금액과 종류별 입력이 둘 다 있다
        final LoanEstimateInput input = new LoanEstimateInput(
                1_000_000_000L, null, 60_000_000L, 100_000_000L,
                999_000_000_000L,
                List.of(new ExistingDebt(DebtType.CREDIT, 50_000_000L)),
                false, false, RateType.VARIABLE);

        // when
        final long annual = input.existingDebtAnnualPayment(RATE);

        // then — 옛 금액(9,990억)이 섞였다면 값이 폭발한다
        assertThat(annual)
                .isEqualTo(new ExistingDebt(DebtType.CREDIT, 50_000_000L).annualPayment(RATE));
    }

    @Test
    @DisplayName("여러 종류를 합산한다")
    void sumsAllDebts() {
        // given
        final LoanEstimateInput input = new LoanEstimateInput(
                1_000_000_000L, null, 60_000_000L, 100_000_000L, 0L,
                List.of(new ExistingDebt(DebtType.CREDIT, 30_000_000L),
                        new ExistingDebt(DebtType.INSTALLMENT, 12_000_000L)),
                false, false, RateType.VARIABLE);

        // when · then
        assertThat(input.existingDebtAnnualPayment(RATE)).isEqualTo(
                new ExistingDebt(DebtType.CREDIT, 30_000_000L).annualPayment(RATE)
                        + new ExistingDebt(DebtType.INSTALLMENT, 12_000_000L).annualPayment(RATE));
    }
}
