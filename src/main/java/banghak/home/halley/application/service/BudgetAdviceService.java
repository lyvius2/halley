package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.budget.*;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class BudgetAdviceService {
    private static final String SYSTEM_PROMPT = "당신은 신혼 예산 계획을 설명하는 도우미다. 계산값을 다시 계산하거나 바꾸지 말고, 대출 승인·투자·가격 확정 조언을 하지 마라. 주어진 수치를 그대로 인용해 절감 후보, 입주 전 필수, 입주 후 구매 가능, 보유품 대체를 한국어로 간결하게 설명하라.";
    private final BudgetPlanService budgetPlanService;
    private final LlmPort llmPort;
    private final LlmModelService llmModelService;

    public BudgetAdviceService(BudgetPlanService budgetPlanService, LlmPort llmPort, LlmModelService llmModelService) {
        this.budgetPlanService = budgetPlanService;
        this.llmPort = llmPort;
        this.llmModelService = llmModelService;
    }

    public Optional<BudgetAdvice> advise(Long planId) {
        if (!llmPort.isEnabled()) return Optional.empty();
        final BudgetPlanAggregate aggregate = budgetPlanService.get(planId);
        final LlmResult result = llmPort.complete(new LlmMessage(SYSTEM_PROMPT, prompt(aggregate), 900,
                llmModelService.modelFor(LlmFeature.RECOMMENDATION)));
        return result.value().map(text -> new BudgetAdvice(text, result.model()));
    }

    String prompt(BudgetPlanAggregate aggregate) {
        final BudgetPlan plan = aggregate.plan();
        final BudgetSummary summary = aggregate.summary();
        final StringJoiner items = new StringJoiner("\n");
        aggregate.items().stream().filter(BudgetItem::includedInBudget).forEach(item -> items.add(
                "- " + item.itemName() + " | " + item.category() + " | 예산 " + item.budgetAmountWon() + "원"));
        return "[확정 계산값 - 변경 금지]\n주택 유형: " + plan.housingType() + "\n주택 가격: " + plan.purchasePrice()
                + "원\n전용면적: " + (plan.exclusiveAreaM2() == null ? "정보 없음" : plan.exclusiveAreaM2())
                + "㎡\n전체 초기 필요자금: " + summary.totalInitialNeed() + "원\n추가 필요 자금: " + summary.additionalFunds()
                + "원\n월 고정 주거비: " + summary.monthlyFixedHousingCost() + "원\n\n[구매 대상 혼수]\n" + items;
    }
}
