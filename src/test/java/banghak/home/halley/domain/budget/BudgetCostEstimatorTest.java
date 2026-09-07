package banghak.home.halley.domain.budget;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetCostEstimatorTest {
    private final BudgetCostEstimator estimator = new BudgetCostEstimator();

    @Test
    @DisplayName("매매가와 면적 및 대출금을 바탕으로 부대비용 참고값을 계산한다")
    void estimatesSaleCosts() {
        // given
        final BudgetPlan plan = plan(HousingType.SALE, 500_000_000L, new BigDecimal("84"));
        final BudgetFinancing financing = financing(300_000_000L);

        // when
        final BudgetCostEstimate result = estimator.estimate(plan, financing);

        // then
        assertThat(result.acquisitionTax()).isEqualTo(5_000_000L);
        assertThat(result.brokerageFee()).isEqualTo(2_000_000L);
        assertThat(result.registrationFee()).isEqualTo(1_400_000L);
        assertThat(result.movingCost()).isEqualTo(1_208_000L);
        assertThat(result.cleaningCost()).isEqualTo(1_108_000L);
    }

    @Test
    @DisplayName("매매가 아닌 주택은 취득세와 등기 및 중개보수를 추정하지 않는다")
    void omitsSaleOnlyCostsForLease() {
        // given
        final BudgetPlan plan = plan(HousingType.JEONSE, 300_000_000L, null);

        // when
        final BudgetCostEstimate result = estimator.estimate(plan, financing(0L));

        // then
        assertThat(result.acquisitionTax()).isZero();
        assertThat(result.brokerageFee()).isZero();
        assertThat(result.registrationFee()).isZero();
        assertThat(result.movingCost()).isZero();
    }

    private BudgetPlan plan(HousingType type, long price, BigDecimal area) {
        return new BudgetPlan(1L, 1L, 1L, "계획", BudgetScenario.RECOMMENDED, null, type, null, null, price,
                area, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
    }

    private BudgetFinancing financing(long amount) {
        return new BudgetFinancing(1L, 1L, amount, null, BigDecimal.ZERO, 360,
                RepaymentType.AMORTIZED, 0L, true, null, null);
    }
}
