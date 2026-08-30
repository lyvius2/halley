package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전세자금대출 산정 (설계 I67)")
class JeonseLoanCalculatorTest {

    private final RegulationParams params = RegulationParams.defaults();
    /** 보증비율 80%, 보증기관 한도 2.22억, 금리 4%, 2년. */
    private final JeonseTerms terms = new JeonseTerms(
            new BigDecimal("0.8"), 222_000_000L, new BigDecimal("0.04"), 2);
    private final JeonseLoanCalculator calculator = new JeonseLoanCalculator(terms);

    @Test
    @DisplayName("보증 한도는 보증금 × 보증비율이다")
    void guaranteeLimitFromDeposit() {
        // given — 보증금 2억, 소득 충분
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(200_000_000L, 200_000_000L, 100_000_000L, 0L), params);

        // then — 2억 × 80% = 1.6억
        assertThat(result.guaranteeLimit()).isEqualTo(160_000_000L);
        assertThat(result.guaranteeRate()).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("보증기관 한도를 넘지 못한다")
    void cappedByGuaranteeInstitution() {
        // given — 보증금 10억이면 80%는 8억이지만 기관 한도가 2.22억이다
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(1_000_000_000L, 500_000_000L, 100_000_000L, 0L), params);

        // then
        assertThat(result.guaranteeLimit()).isEqualTo(222_000_000L);
        assertThat(result.guaranteeCap()).isEqualTo(222_000_000L);
    }

    @Test
    @DisplayName("DSR은 이자만 본다 — 원금까지 상환한다고 보면 한도가 크게 낮아진다")
    void dsrCountsInterestOnly() {
        // given — 연소득 5천만, DSR 40% → 연간 여력 2천만
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(1_000_000_000L, 50_000_000L, 0L, 0L), params);

        // then — 이자만이므로 원금 한도 = 2천만 ÷ 5%(4% + 스트레스 1%) = 4억
        assertThat(result.dsrCapacity()).isEqualTo(20_000_000L);
        assertThat(result.dsrLimit()).isEqualTo(400_000_000L);
        // 주담대(원리금균등 30년)였다면 2.7억 남짓으로 훨씬 낮게 나온다
        final LoanEstimateResult asMortgage = new LoanCalculator(params.ltvRate(), params.totalCap())
                .estimate(new LoanEstimateInput(1_000_000_000L,
                        CollateralValuation.of(1_000_000_000L, CollateralSource.ASKING_PRICE),
                        50_000_000L, 0L, 0L, List.of(), false, true, RateType.VARIABLE), params);
        assertThat(result.dsrLimit()).isGreaterThan(asMortgage.dsrLimit());
    }

    @Test
    @DisplayName("최종 한도는 보증 한도와 DSR 한도 중 작은 쪽이다")
    void finalLimitIsTheSmaller() {
        // given — 소득이 낮아 DSR이 묶는다
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(200_000_000L, 20_000_000L, 0L, 0L), params);

        // then — 보증 1.6억 vs DSR (800만 ÷ 5%) = 1.6억... 소득을 더 낮춰 확인한다
        assertThat(result.finalLimit()).isEqualTo(Math.min(result.guaranteeLimit(), result.dsrLimit()));
    }

    @Test
    @DisplayName("필요 현금은 보증금에서 대출을 뺀 값이다 — 취득세가 없다")
    void requiredCashHasNoAcquisitionTax() {
        // given
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(200_000_000L, 200_000_000L, 100_000_000L, 0L), params);

        // then — 2억 − 1.6억 = 4천만. 소유권이 넘어오지 않으므로 취득세가 없다
        assertThat(result.requiredCash()).isEqualTo(40_000_000L);
    }

    @Test
    @DisplayName("월 상환액은 이자만이다 — 만기일시상환")
    void monthlyPaymentIsInterestOnly() {
        // given
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(200_000_000L, 200_000_000L, 100_000_000L, 0L), params);

        // then — 실제로 내는 이자는 실금리 기준이다 (설계 I97). 1.6억 × 4% ÷ 12 = 약 53.3만원.
        // 예전에는 스트레스를 얹은 5%로 계산해 실제보다 많아 보였다
        assertThat(result.monthlyPayment()).isBetween(530_000L, 540_000L);
        assertThat(result.termMonths()).isEqualTo(24);
    }

    @Test
    @DisplayName("기존 대출이 있으면 DSR 여력에서 먼저 뺀다")
    void existingLoanReducesCapacity() {
        // when
        final JeonseEstimateResult none = calculator.estimate(
                new JeonseEstimateInput(1_000_000_000L, 60_000_000L, 0L, 0L), params);
        final JeonseEstimateResult withLoan = calculator.estimate(
                new JeonseEstimateInput(1_000_000_000L, 60_000_000L, 0L, 200_000_000L), params);

        // then
        assertThat(withLoan.dsrLimit()).isLessThan(none.dsrLimit());
        assertThat(withLoan.existingLoanAnnual()).isPositive();
    }

    @Test
    @DisplayName("기존 대출이 여력을 넘으면 한도는 0이다")
    void hugeExistingLoanZeroesLimit() {
        // when
        final JeonseEstimateResult result = calculator.estimate(
                new JeonseEstimateInput(200_000_000L, 20_000_000L, 0L, 3_000_000_000L), params);

        // then
        assertThat(result.dsrLimit()).isZero();
        assertThat(result.finalLimit()).isZero();
        assertThat(result.requiredCash()).isEqualTo(200_000_000L);
    }
}
