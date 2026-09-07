package banghak.home.halley.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("신혼 예산 도메인 모델")
class BudgetDomainTest {

    @Test
    @DisplayName("자산을 제외하면 실제 투입 가능 금액은 0원이 된다")
    void excludedAssetIsNotInvestable() {
        // given
        final BudgetAsset asset = new BudgetAsset(1L, 2L, 3L, AssetType.VEHICLE,
                "자동차", 30_000_000L, true, 30_000_000L, "MANUAL", null, null, null);

        // when
        final long investable = asset.effectiveInvestableAmount();

        // then
        assertThat(investable).isZero();
    }

    @Test
    @DisplayName("보유하지 않고 선택한 혼수 품목만 예산에 포함된다")
    void selectedAndNotOwnedItemIsIncluded() {
        // given
        final BudgetItem item = new BudgetItem(1L, 2L, "seed-00", "가전", "TV",
                true, false, true, false, 2_000_000L, null, null, null,
                null, null, null, null, null, null, null, null);

        // when
        final boolean included = item.includedInBudget();

        // then
        assertThat(included).isTrue();
    }

    @Test
    @DisplayName("음수 금액을 가진 예산 계획은 생성할 수 없다")
    void negativePlanAmountIsRejected() {
        // given
        final long invalidPrice = -1L;

        // when
        final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BudgetPlan(1L, 2L, 3L, "신혼 계획", BudgetScenario.RECOMMENDED,
                        null, HousingType.SALE, null, null, invalidPrice, null,
                        0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null));

        // then
        assertThat(thrown).hasMessageContaining("purchasePrice");
    }

    @Test
    @DisplayName("금융 조건의 기본 상환 방식은 원리금균등이다")
    void financingDefaultsToAmortized() {
        // given
        final BudgetFinancing financing = new BudgetFinancing(1L, 2L, 100_000_000L,
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(3.5), 360, null,
                500_000L, true, null, null);

        // when
        final RepaymentType repaymentType = financing.repaymentType();

        // then
        assertThat(repaymentType).isEqualTo(RepaymentType.AMORTIZED);
    }
}
