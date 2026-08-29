package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("담보가치·방공제 반영 대출 산정 (설계 I64)")
class LoanCalculatorCollateralTest {

    private final RegulationParams params = RegulationParams.defaults();
    private final LoanCalculator calculator = new LoanCalculator(params.ltvRate(), params.totalCap());

    @Test
    @DisplayName("LTV는 호가가 아니라 담보가치에 매긴다")
    void ltvUsesCollateralNotAskingPrice() {
        // given — 호가 15억, KB시세 13.5억 (설계 9.2 실측)
        final LoanEstimateResult result = calculator.estimate(new LoanEstimateInput(
                1_500_000_000L,
                CollateralValuation.of(1_350_000_000L, CollateralSource.KB_PRICE),
                200_000_000L, 500_000_000L, 0L, false, true), params);

        // then — 13.5억 × 40% = 5.4억. 호가로 계산했다면 6억이 나왔을 것이다
        assertThat(result.ltvLimit()).isEqualTo(540_000_000L);
        assertThat(result.collateralValue()).isEqualTo(1_350_000_000L);
        assertThat(result.collateralSource()).isEqualTo(CollateralSource.KB_PRICE);
        // 필요 현금·취득세는 실제로 지불하는 호가 기준이다
        assertThat(result.requiredCash()).isEqualTo(1_500_000_000L - result.finalLimit());
    }

    @Test
    @DisplayName("방공제를 LTV 한도에서 뺀다 — 빼먹으면 수천만 원 높게 나온다")
    void subtractsLeaseDeduction() {
        // given — MCI 미가입
        final LoanEstimateInput input = new LoanEstimateInput(
                1_000_000_000L,
                CollateralValuation.of(1_000_000_000L, CollateralSource.KB_PRICE),
                200_000_000L, 500_000_000L, 0L, false, false);

        // when
        final LoanEstimateResult result = calculator.estimate(input, params);

        // then — 10억 × 40% = 4억에서 방공제 5,500만원을 뺀 3.45억
        assertThat(result.leaseDeduction()).isEqualTo(params.leaseDeduction());
        assertThat(result.ltvLimit()).isEqualTo(400_000_000L - params.leaseDeduction());
    }

    @Test
    @DisplayName("MCI/MCG에 가입하면 방공제를 빼지 않는다")
    void mortgageInsuranceWaivesDeduction() {
        // given — 같은 조건에 MCI 가입만 다르다
        final CollateralValuation collateral =
                CollateralValuation.of(1_000_000_000L, CollateralSource.KB_PRICE);
        final LoanEstimateResult without = calculator.estimate(new LoanEstimateInput(
                1_000_000_000L, collateral, 200_000_000L, 500_000_000L, 0L, false, false), params);
        final LoanEstimateResult with = calculator.estimate(new LoanEstimateInput(
                1_000_000_000L, collateral, 200_000_000L, 500_000_000L, 0L, false, true), params);

        // then
        assertThat(with.leaseDeduction()).isZero();
        assertThat(with.ltvLimit() - without.ltvLimit()).isEqualTo(params.leaseDeduction());
    }

    @Test
    @DisplayName("방공제가 LTV 한도보다 크면 한도는 0이다 — 음수로 내려가지 않는다")
    void deductionCannotPushLimitNegative() {
        // given — 담보가치 1억, LTV 40% = 4천만원 < 방공제 5,500만원
        final LoanEstimateResult result = calculator.estimate(new LoanEstimateInput(
                100_000_000L,
                CollateralValuation.of(100_000_000L, CollateralSource.KB_PRICE),
                200_000_000L, 500_000_000L, 0L, false, false), params);

        // then
        assertThat(result.ltvLimit()).isZero();
        assertThat(result.finalLimit()).isZero();
        assertThat(result.requiredCash()).isEqualTo(100_000_000L);
    }

    @Test
    @DisplayName("담보가치 출처와 표본 수를 결과에 함께 실어 보낸다")
    void carriesProvenance() {
        // given — 실거래 2건으로 매긴 값
        final LoanEstimateResult result = calculator.estimate(new LoanEstimateInput(
                1_000_000_000L,
                new CollateralValuation(950_000_000L, CollateralSource.RECENT_TRADE, 2),
                200_000_000L, 500_000_000L, 0L, false, true), params);

        // then — 표본이 3건 미만이라 신뢰할 만하지 않다고 표시된다
        assertThat(result.collateralSource()).isEqualTo(CollateralSource.RECENT_TRADE);
        assertThat(result.collateralSampleCount()).isEqualTo(2);
        assertThat(result.collateralReliable()).isFalse();
    }
}
