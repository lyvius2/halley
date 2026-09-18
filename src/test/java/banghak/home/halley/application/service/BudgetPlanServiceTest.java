package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.*;
import banghak.home.halley.domain.budget.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetPlanServiceTest {
    @Mock HouseholdBudgetPlanRepository plans;
    @Mock HouseholdBudgetAssetRepository assets;
    @Mock HouseholdBudgetItemRepository items;
    @Mock HouseholdBudgetItemCatalogRepository catalog;
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
        assertThat(aggregate.summary().totalInitialNeed()).isZero();
        assertThat(aggregate.summary().monthlyFixedHousingCost()).isZero();
        verify(financing).findByPlanId(10L);
    }

    @Test
    @DisplayName("사용자가 추가한 혼수 품목은 기본 카탈로그 항목으로 취급하지 않고 저장한다")
    void addCustomItem() {
        // given
        final BudgetPlan plan = new BudgetPlan(10L, 20L, 1L, "계획", BudgetScenario.RECOMMENDED,
                null, HousingType.SALE, null, null, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
        final BudgetItem requested = new BudgetItem(null, 10L, "client-key", "가구", "협탁", true,
                true, true, false, 150_000L, "후보", "https://example.com/candidate", null,
                null, null, null, ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null);
        when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(accessGuard.currentGroupId()).thenReturn(Optional.of(20L));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(items.save(any(BudgetItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        final BudgetItem saved = service.addItem(10L, requested);

        // then
        assertThat(saved.seedKey()).isNull();
        assertThat(saved.required()).isFalse();
        assertThat(saved.recommended()).isFalse();
        assertThat(saved.selected()).isTrue();
        verify(items).save(saved);
        verify(plans).update(withScenario(plan, BudgetScenario.CUSTOM));
    }

    @Test
    @DisplayName("기존 계획에는 없는 기본 혼수 품목만 추가한다")
    void addMissingCatalogItems() {
        // given
        final BudgetPlan plan = new BudgetPlan(10L, 20L, 1L, "계획", BudgetScenario.RECOMMENDED,
                null, HousingType.SALE, null, null, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
        final BudgetItem existing = new BudgetItem(1L, 10L, "bed", "가구", "침대", true,
                true, true, false, 1_000_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null);
        final BudgetItemCatalog bed = new BudgetItemCatalog(1L, "bed", "가구", "침대", true,
                true, 1_000_000L, null, null, null, null, null, null);
        final BudgetItemCatalog lamp = new BudgetItemCatalog(2L, "lamp", "가전", "조명", false,
                true, 100_000L, null, null, null, null, null, null);
        when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(accessGuard.currentGroupId()).thenReturn(Optional.of(20L));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(items.findByPlanId(10L)).thenReturn(List.of(existing));
        when(catalog.findAll()).thenReturn(List.of(bed, lamp));

        // when
        final CatalogItemsImportResult result = service.addMissingCatalogItems(10L);

        // then
        assertThat(result.addedCount()).isOne();
        verify(items).save(new BudgetItem(null, 10L, "lamp", "가전", "조명", false,
                true, true, false, 100_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null));
    }

    @Test
    @DisplayName("추천 시나리오는 필수 품목과 추천 품목을 함께 선택한다")
    void recommendedScenarioIncludesRequiredAndRecommendedItems() {
        // given
        final BudgetPlan plan = new BudgetPlan(10L, 20L, 1L, "계획", BudgetScenario.MINIMUM,
                null, HousingType.SALE, null, null, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null);
        final BudgetItem required = new BudgetItem(1L, 10L, "bed", "가구", "침대", true,
                false, false, false, 1_000_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null);
        final BudgetItem recommended = new BudgetItem(2L, 10L, "dishwasher", "가전", "식기세척기", false,
                true, false, false, 1_000_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null);
        when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(accessGuard.currentGroupId()).thenReturn(Optional.of(20L));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(items.findByPlanId(10L)).thenReturn(List.of(required, recommended));
        when(plans.update(any(BudgetPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.applyScenario(10L, BudgetScenario.RECOMMENDED);

        // then
        verify(items).update(argThat(item -> item.id().equals(required.id()) && item.selected()));
        verify(items).update(argThat(item -> item.id().equals(recommended.id()) && item.selected()));
    }

    private BudgetPlan withScenario(BudgetPlan plan, BudgetScenario scenario) {
        return new BudgetPlan(plan.id(), plan.groupId(), plan.createdBy(), plan.planName(), scenario,
                plan.selectedPropertyId(), plan.housingType(), plan.region(), plan.houseName(), plan.purchasePrice(),
                plan.exclusiveAreaM2(), plan.contractCash(), plan.balanceCash(), plan.acquisitionTax(), plan.brokerageFee(),
                plan.registrationFee(), plan.movingCost(), plan.cleaningCost(), plan.householdReserveCost(), plan.acquisitionTaxSource(), plan.brokerageFeeSource(),
                plan.registrationFeeSource(), plan.movingCostSource(), plan.cleaningCostSource(), plan.otherInitialCost(),
                plan.parentSupport(), plan.otherFunds(), plan.monthlyManagementFee(), plan.monthlyOtherHousingCost(),
                plan.createdAt(), plan.updatedAt());
    }
}
