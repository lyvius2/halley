package banghak.home.halley.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("신혼 예산 계산기")
class BudgetCalculatorTest {

    private final BudgetCalculator calculator = new BudgetCalculator();

    @Test
    @DisplayName("주택 부대비용과 선택한 혼수만 초기 필요자금에 포함한다")
    void calculatesInitialNeed() {
        // given
        final BudgetPlan plan = plan(500_000_000L, 100_000_000L, 300_000_000L,
                10_000_000L, 5_000_000L, 2_000_000L, 1_000_000L, 3_000_000L, 0L, 0L);
        final BudgetFinancing financing = financing(200_000_000L, RepaymentType.INTEREST_ONLY);
        final BudgetItem purchased = item(true, false, 20_000_000L);
        final BudgetItem owned = item(true, true, 99_000_000L);

        // when
        final BudgetSummary summary = calculator.calculate(plan, financing, List.of(),
                List.of(purchased, owned));

        // then
        assertThat(summary.ownHousingCash()).isEqualTo(400_000_000L);
        assertThat(summary.housingAncillaryCost()).isEqualTo(17_000_000L);
        assertThat(summary.householdBudget()).isEqualTo(20_000_000L);
        assertThat(summary.totalInitialNeed()).isEqualTo(441_000_000L);
    }

    @Test
    @DisplayName("제외 자산과 지원금을 반영해 부족 자금을 계산한다")
    void calculatesShortfallFromAssets() {
        // given
        final BudgetPlan plan = plan(300_000_000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                10_000_000L, 5_000_000L);
        final BudgetFinancing financing = financing(100_000_000L, RepaymentType.INTEREST_ONLY);
        final BudgetAsset included = asset(50_000_000L, false, 40_000_000L);
        final BudgetAsset excluded = asset(80_000_000L, true, 80_000_000L);

        // when
        final BudgetSummary summary = calculator.calculate(plan, financing,
                List.of(included, excluded), List.of());

        // then
        assertThat(summary.investableAssets()).isEqualTo(40_000_000L);
        assertThat(summary.securedFunds()).isEqualTo(155_000_000L);
        assertThat(summary.additionalFunds()).isEqualTo(45_000_000L);
        assertThat(summary.hasShortfall()).isTrue();
    }

    @Test
    @DisplayName("원리금균등 대출의 월 원리금을 참고용으로 계산한다")
    void calculatesAmortizedMonthlyPayment() {
        // given
        final BudgetPlan plan = plan(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        final BudgetFinancing financing = financing(100_000_000L, RepaymentType.AMORTIZED);

        // when
        final BudgetSummary summary = calculator.calculate(plan, financing, List.of(), List.of());

        // then
        assertThat(summary.monthlyPrincipalInterest()).isEqualTo(449_045L);
    }

    private static BudgetPlan plan(long price, long contract, long balance, long tax,
                                   long brokerage, long registration, long moving, long cleaning,
                                   long parentSupport, long otherFunds) {
        return new BudgetPlan(1L, 2L, 3L, "테스트 계획", BudgetScenario.RECOMMENDED, null,
                HousingType.SALE, null, "테스트 주택", price, null, contract, balance, tax,
                brokerage, registration, moving, cleaning, 0L, parentSupport, otherFunds,
                0L, 0L, null, null);
    }

    private static BudgetFinancing financing(long loanAmount, RepaymentType type) {
        return new BudgetFinancing(1L, 1L, loanAmount, BigDecimal.ZERO,
                BigDecimal.valueOf(3.5), 360, type, 0L, true, null, null);
    }

    private static BudgetAsset asset(long value, boolean excluded, long investable) {
        return new BudgetAsset(1L, 1L, 1L, AssetType.FINANCIAL, "자산", value, excluded,
                investable, "MANUAL", null, null, null);
    }

    private static BudgetItem item(boolean selected, boolean owned, long amount) {
        return new BudgetItem(1L, 1L, "seed-00", "가전", "품목", true, false, selected, owned,
                amount, null, null, null, null, null, null, null, null, null, null, null);
    }
}
