package banghak.home.halley.domain.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("스트레스 DSR (설계 I97)")
class StressDsrTest {

    private static final RegulationParams PARAMS = RegulationParams.defaults();

    @Test
    @DisplayName("월 상환액은 실금리로 계산한다 — 스트레스는 한도를 역산할 때만 쓴다")
    void monthlyPaymentUsesActualRate() {
        // when
        final LoanEstimateResult result = estimate(RateType.VARIABLE);

        // then — 표시 금리에 스트레스가 섞이면 실제로 내는 돈보다 커 보인다
        assertThat(result.monthlyRate())
                .isEqualTo(PARAMS.interestRate().doubleValue() / 12.0);
        assertThat(result.dsrMonthlyRate()).isGreaterThan(result.monthlyRate());
    }

    @Test
    @DisplayName("고정금리는 스트레스가 붙지 않는다 — 만기까지 고정이면 오를 위험이 없다")
    void fixedRateHasNoStress() {
        // when
        final LoanEstimateResult fixed = estimate(RateType.FIXED);

        // then
        assertThat(fixed.dsrMonthlyRate()).isEqualTo(fixed.monthlyRate());
        assertThat(PARAMS.effectiveStressRate(RateType.FIXED)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("금리유형이 한도를 가른다 — 변동일수록 적게 빌린다")
    void rateTypeChangesTheLimit() {
        // when
        final long variable = estimate(RateType.VARIABLE).dsrLimit();
        final long mixed = estimate(RateType.MIXED).dsrLimit();
        final long fixed = estimate(RateType.FIXED).dsrLimit();

        // then — 스트레스가 클수록 한도가 준다
        assertThat(variable).isLessThan(mixed);
        assertThat(mixed).isLessThan(fixed);
    }

    @Test
    @DisplayName("단계 적용률을 낮추면 스트레스도 그만큼 줄어든다")
    void applyRatioScalesStress() {
        // given — 규제가 단계적으로 올라오던 시기(50%)
        final RegulationParams half = withApplyRatio(new BigDecimal("0.5"));

        // when
        final BigDecimal full = PARAMS.effectiveStressRate(RateType.VARIABLE);
        final BigDecimal halved = half.effectiveStressRate(RateType.VARIABLE);

        // then
        assertThat(halved).isEqualByComparingTo(full.multiply(new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("금리유형을 안 주면 변동으로 본다 — 모르면 보수적으로")
    void defaultsToVariable() {
        assertThat(PARAMS.effectiveStressRate(null))
                .isEqualByComparingTo(PARAMS.effectiveStressRate(RateType.VARIABLE));
    }

    private LoanEstimateResult estimate(RateType rateType) {
        return new LoanCalculator(PARAMS.ltvRate(), PARAMS.totalCap())
                .estimate(new LoanEstimateInput(
                        1_000_000_000L,
                        new CollateralValuation(900_000_000L, CollateralSource.KB_PRICE, 0),
                        80_000_000L, 200_000_000L, 0L, List.of(), false, false, rateType),
                        PARAMS);
    }

    private RegulationParams withApplyRatio(BigDecimal ratio) {
        return new RegulationParams(
                PARAMS.ltvRate(), PARAMS.totalCap(), PARAMS.dsrRatio(), PARAMS.interestRate(),
                PARAMS.stressRate(), PARAMS.termYears(), PARAMS.acquisitionTaxRate(),
                PARAMS.firstHomeDiscount(), PARAMS.leaseDeduction(), PARAMS.officialPriceRatio(),
                ratio);
    }
}
