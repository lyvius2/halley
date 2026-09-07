package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.LlmPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetAdviceServiceTest {
    @Mock BudgetPlanService budgetPlanService;
    @Mock LlmPort llmPort;
    @Mock LlmModelService llmModelService;
    @InjectMocks BudgetAdviceService service;

    @Test
    @DisplayName("LLM이 설정되지 않으면 예산 계산을 막지 않고 제안 없음으로 돌려준다")
    void returnsEmptyWhenLlmIsDisabled() {
        // given
        when(llmPort.isEnabled()).thenReturn(false);

        // when
        final var advice = service.advise(1L);

        // then
        assertThat(advice).isEmpty();
    }
}
