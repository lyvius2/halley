package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.budget.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class HouseholdBudgetRepositoryIntegrationTest {
    @Autowired private HouseholdBudgetPlanRepository plans;
    @Autowired private HouseholdBudgetAssetRepository assets;
    @Autowired private HouseholdBudgetItemRepository items;
    @Autowired private HouseholdBudgetFinancingRepository financing;

    @Test
    @DisplayName("예산 계획과 하위 자원을 저장하고 다시 읽는다")
    void budgetResourcesRoundTrip() {
        // given
        final BudgetPlan plan = new BudgetPlan(null, 1L, 1L, "테스트 계획", BudgetScenario.RECOMMENDED,
                null, HousingType.SALE, "서울", "테스트 주택", 500_000_000L, new BigDecimal("59.9"),
                50_000_000L, 100_000_000L, 5_000_000L, 2_000_000L, 1_000_000L, 3_000_000L,
                1_000_000L, CostInputSource.AUTO_ESTIMATE, CostInputSource.AUTO_ESTIMATE,
                CostInputSource.AUTO_ESTIMATE, CostInputSource.AUTO_ESTIMATE, CostInputSource.AUTO_ESTIMATE,
                0L, 10_000_000L, 0L, 200_000L, 50_000L, null, null);

        // when
        final BudgetPlan savedPlan = plans.save(plan);
        final BudgetAsset savedAsset = assets.save(new BudgetAsset(null, savedPlan.id(), 1L,
                AssetType.FINANCIAL, "예금", 100_000_000L, false, 80_000_000L, "MANUAL", null, null, null));
        final BudgetItem savedItem = items.save(new BudgetItem(null, savedPlan.id(), "seed-1", "가전", "냉장고",
                true, true, true, false, 2_000_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null));
        final BudgetFinancing savedFinancing = financing.save(new BudgetFinancing(null, savedPlan.id(),
                300_000_000L, new BigDecimal("0.6"), new BigDecimal("0.04"), 360,
                RepaymentType.AMORTIZED, 1_432_000L, true, null, null));

        // then
        assertThat(plans.findById(savedPlan.id())).hasValueSatisfying(reloaded ->
                assertThat(reloaded.registrationFeeSource()).isEqualTo(CostInputSource.AUTO_ESTIMATE));
        assertThat(assets.findByPlanId(savedPlan.id())).extracting(BudgetAsset::id).contains(savedAsset.id());
        assertThat(items.findByPlanId(savedPlan.id())).extracting(BudgetItem::id).contains(savedItem.id());
        assertThat(financing.findByPlanId(savedPlan.id())).contains(savedFinancing);
    }
}
