package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.BudgetPlanService;
import banghak.home.halley.domain.budget.BudgetPlan;
import banghak.home.halley.domain.budget.AssetType;
import banghak.home.halley.domain.budget.BudgetAsset;
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
}
