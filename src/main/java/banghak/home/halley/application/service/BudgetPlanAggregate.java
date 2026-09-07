package banghak.home.halley.application.service;

import banghak.home.halley.domain.budget.*;
import java.util.List;

public record BudgetPlanAggregate(BudgetPlan plan, BudgetFinancing financing,
                                  List<BudgetAsset> assets, List<BudgetItem> items) {
    public BudgetPlanAggregate {
        assets = List.copyOf(assets == null ? List.of() : assets);
        items = List.copyOf(items == null ? List.of() : items);
    }
}
