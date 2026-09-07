package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.*;
import banghak.home.halley.config.exception.NoGroupException;
import banghak.home.halley.domain.budget.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BudgetPlanService {
    private final HouseholdBudgetPlanRepository planRepository;
    private final HouseholdBudgetAssetRepository assetRepository;
    private final HouseholdBudgetItemRepository itemRepository;
    private final HouseholdBudgetFinancingRepository financingRepository;
    private final PropertyAccessGuard accessGuard;

    public BudgetPlanService(HouseholdBudgetPlanRepository planRepository,
                             HouseholdBudgetAssetRepository assetRepository,
                             HouseholdBudgetItemRepository itemRepository,
                             HouseholdBudgetFinancingRepository financingRepository,
                             PropertyAccessGuard accessGuard) {
        this.planRepository = planRepository;
        this.assetRepository = assetRepository;
        this.itemRepository = itemRepository;
        this.financingRepository = financingRepository;
        this.accessGuard = accessGuard;
    }

    public List<BudgetPlan> findMine() {
        return planRepository.findByGroupId(requireGroupId());
    }

    public BudgetPlanAggregate get(Long planId) {
        final BudgetPlan plan = requirePlan(planId);
        return new BudgetPlanAggregate(plan, financingRepository.findByPlanId(planId).orElse(null),
                assetRepository.findByPlanId(planId), itemRepository.findByPlanId(planId));
    }

    public BudgetPlan create(BudgetPlan plan) {
        final Long groupId = requireGroupId();
        if (!groupId.equals(plan.groupId())) {
            throw new NoGroupException();
        }
        return planRepository.save(plan);
    }

    public BudgetAsset addAsset(BudgetAsset asset) {
        requirePlan(asset.planId());
        return assetRepository.save(asset);
    }

    public BudgetItem addItem(BudgetItem item) {
        requirePlan(item.planId());
        return itemRepository.save(item);
    }

    public BudgetFinancing saveFinancing(BudgetFinancing financing) {
        requirePlan(financing.planId());
        return financingRepository.save(financing);
    }

    private BudgetPlan requirePlan(Long planId) {
        final BudgetPlan plan = planRepository.findById(planId).orElseThrow(NoGroupException::new);
        if (!accessGuard.isAdmin() && !requireGroupId().equals(plan.groupId())) {
            throw new NoGroupException();
        }
        return plan;
    }

    private Long requireGroupId() {
        return accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
    }
}
