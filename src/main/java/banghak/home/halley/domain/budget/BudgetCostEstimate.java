package banghak.home.halley.domain.budget;

import java.util.List;

/** 저장 전 사용자에게 보여 주는 부대비용 참고 추정값. 금액은 원 단위다. */
public record BudgetCostEstimate(
        long acquisitionTax,
        long brokerageFee,
        long registrationFee,
        long movingCost,
        long cleaningCost,
        List<String> explanations,
        List<String> cautions
) { }
