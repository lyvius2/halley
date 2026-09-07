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
    private final HouseholdBudgetItemCatalogRepository catalogRepository;
    private final PropertyAccessGuard accessGuard;
    private final BudgetCalculator calculator = new BudgetCalculator();

    public BudgetPlanService(HouseholdBudgetPlanRepository planRepository,
                             HouseholdBudgetAssetRepository assetRepository,
                             HouseholdBudgetItemRepository itemRepository,
                             HouseholdBudgetFinancingRepository financingRepository,
                             HouseholdBudgetItemCatalogRepository catalogRepository,
                             PropertyAccessGuard accessGuard) {
        this.planRepository = planRepository;
        this.assetRepository = assetRepository;
        this.itemRepository = itemRepository;
        this.financingRepository = financingRepository;
        this.catalogRepository = catalogRepository;
        this.accessGuard = accessGuard;
    }

    public List<BudgetPlan> findMine() {
        return planRepository.findByGroupId(requireGroupId());
    }

    public BudgetPlanAggregate get(Long planId) {
        final BudgetPlan plan = requirePlan(planId);
        final BudgetFinancing financing = financingRepository.findByPlanId(planId)
                .orElseGet(() -> emptyFinancing(planId));
        final List<BudgetAsset> assets = assetRepository.findByPlanId(planId);
        final List<BudgetItem> items = itemRepository.findByPlanId(planId);
        return new BudgetPlanAggregate(plan, financing, assets, items,
                calculator.calculate(plan, financing, assets, items));
    }

    public BudgetPlan create(BudgetPlan plan) {
        final Long groupId = requireGroupId();
        if (!groupId.equals(plan.groupId())) {
            throw new NoGroupException();
        }
        final BudgetPlan saved = planRepository.save(plan);
        catalogRepository.findAll().forEach(catalog -> itemRepository.save(new BudgetItem(
                null, saved.id(), catalog.seedKey(), catalog.category(), catalog.itemName(), catalog.required(),
                catalog.recommended(), selectedFor(plan.scenario(), catalog), false, catalog.defaultBudgetAmountWon(),
                catalog.candidateName(), catalog.candidateUrl(), null, catalog.alternativeName(), catalog.alternativeUrl(),
                null, ProductFetchStatus.NOT_FETCHED, ProductFetchStatus.NOT_FETCHED, catalog.note(), null, null)));
        return saved;
    }

    private boolean selectedFor(BudgetScenario scenario, BudgetItemCatalog catalog) {
        return switch (scenario) {
            case MINIMUM -> catalog.required();
            case RECOMMENDED -> catalog.recommended();
            case COMFORTABLE -> true;
            case CUSTOM -> catalog.required();
        };
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

    private BudgetFinancing emptyFinancing(Long planId) {
        return new BudgetFinancing(null, planId, 0L, null, java.math.BigDecimal.ZERO, 360,
                RepaymentType.AMORTIZED, 0L, true, null, null);
    }
}
