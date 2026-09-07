package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.BudgetPlanService;
import banghak.home.halley.application.service.ProductPreviewService;
import banghak.home.halley.application.service.BudgetAdviceService;
import banghak.home.halley.domain.budget.BudgetPlan;
import banghak.home.halley.domain.budget.AssetType;
import banghak.home.halley.domain.budget.BudgetAsset;
import banghak.home.halley.domain.budget.BudgetFinancing;
import banghak.home.halley.domain.budget.RepaymentType;
import banghak.home.halley.domain.budget.BudgetItem;
import banghak.home.halley.domain.budget.ProductFetchStatus;
import banghak.home.halley.domain.budget.BudgetScenario;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BudgetControllerTest {
    @Mock BudgetPlanService service;
    @Mock ProductPreviewService productPreviewService;
    @Mock BudgetAdviceService budgetAdviceService;
    @InjectMocks BudgetController controller;

    @Test
    @DisplayName("예산 계획 목록 API가 Service 결과를 반환한다")
    void listDelegatesToService() {
        // given
        final List<BudgetPlan> expected = List.of();
        when(service.findMine()).thenReturn(expected);

        // when
        final List<BudgetPlan> actual = controller.list();

        // then
        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("계획 경로와 일치하는 자산을 Service에 전달한다")
    void addAssetDelegatesToService() {
        // given
        final BudgetAsset asset = new BudgetAsset(null, 10L, 2L, AssetType.FINANCIAL,
                "예금", 1_000_000L, false, 1_000_000L, "MANUAL", null, null, null);
        when(service.addAsset(asset)).thenReturn(asset);

        // when
        final BudgetAsset saved = controller.addAsset(10L, asset);

        // then
        assertThat(saved).isEqualTo(asset);
        verify(service).addAsset(asset);
    }

    @Test
    @DisplayName("계획 경로와 일치하는 대출 조건을 Service에 전달한다")
    void saveFinancingDelegatesToService() {
        // given
        final BudgetFinancing financing = new BudgetFinancing(null, 10L, 100_000_000L, null,
                new BigDecimal("3.5"), 360, RepaymentType.AMORTIZED, 0L, true, null, null);
        when(service.saveFinancing(financing)).thenReturn(financing);

        // when
        final BudgetFinancing saved = controller.saveFinancing(10L, financing);

        // then
        assertThat(saved).isEqualTo(financing);
        verify(service).saveFinancing(financing);
    }

    @Test
    @DisplayName("계획과 품목 경로가 일치하면 혼수 품목을 수정한다")
    void updateItemDelegatesToService() {
        // given
        final BudgetItem item = new BudgetItem(3L, 10L, "seed-1", "가전", "냉장고", true,
                true, true, false, 1_000_000L, null, null, null, null, null, null,
                ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, null, null, null);
        when(service.updateItem(item)).thenReturn(item);

        // when
        final BudgetItem saved = controller.updateItem(10L, 3L, item);

        // then
        assertThat(saved).isEqualTo(item);
        verify(service).updateItem(item);
    }

    @Test
    @DisplayName("선택한 시나리오를 계획 Service에 전달한다")
    void applyScenarioDelegatesToService() {
        // given
        final BudgetPlan plan = null;
        when(service.applyScenario(10L, BudgetScenario.MINIMUM)).thenReturn(plan);

        // when
        final BudgetPlan result = controller.applyScenario(10L, BudgetScenario.MINIMUM);

        // then
        assertThat(result).isNull();
        verify(service).applyScenario(10L, BudgetScenario.MINIMUM);
    }
}
