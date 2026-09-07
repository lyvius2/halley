package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.*;
import banghak.home.halley.domain.budget.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetPlanServiceTest {
    @Mock HouseholdBudgetPlanRepository plans;
    @Mock HouseholdBudgetAssetRepository assets;
    @Mock HouseholdBudgetItemRepository items;
    @Mock HouseholdBudgetFinancingRepository financing;
    @Mock PropertyAccessGuard accessGuard;
    @InjectMocks BudgetPlanService service;

    @Test
    @DisplayName("현재 그룹의 예산 계획을 구성 요소와 함께 조회한다")
    void getAggregateForCurrentGroup() {
        // given
        final BudgetPlan plan = new BudgetPlan(10L, 20L, 1L, "계획", BudgetScenario.RECOMMENDED,
                null, HousingType.SALE, null, null, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
        when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(accessGuard.currentGroupId()).thenReturn(Optional.of(20L));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(assets.findByPlanId(10L)).thenReturn(java.util.List.of());
        when(items.findByPlanId(10L)).thenReturn(java.util.List.of());
        when(financing.findByPlanId(10L)).thenReturn(Optional.empty());

        // when
        final BudgetPlanAggregate aggregate = service.get(10L);

        // then
        assertThat(aggregate.plan()).isEqualTo(plan);
        assertThat(aggregate.assets()).isEmpty();
        verify(financing).findByPlanId(10L);
    }
}
