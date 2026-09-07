package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.*;
import banghak.home.halley.config.exception.NoGroupException;
import banghak.home.halley.domain.budget.*;
import banghak.home.halley.domain.user.User;
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
    private final BudgetCostEstimator costEstimator = new BudgetCostEstimator();

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

    public BudgetPlan createDefault() {
        final Long groupId = requireGroupId();
        final Long userId = accessGuard.currentUser().map(User::id).orElseThrow(NoGroupException::new);
        return create(new BudgetPlan(null, groupId, userId, "새 신혼 예산 계획", BudgetScenario.RECOMMENDED,
                null, HousingType.SALE, null, null, 0L, null, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null));
    }

    public BudgetPlan update(BudgetPlan plan) {
        final BudgetPlan existing = requirePlan(plan.id());
        return planRepository.update(new BudgetPlan(existing.id(), existing.groupId(), existing.createdBy(),
                plan.planName(), plan.scenario(), plan.selectedPropertyId(), plan.housingType(), plan.region(),
                plan.houseName(), plan.purchasePrice(), plan.exclusiveAreaM2(), plan.contractCash(), plan.balanceCash(),
                plan.acquisitionTax(), plan.brokerageFee(), plan.registrationFee(), plan.movingCost(), plan.cleaningCost(),
                plan.otherInitialCost(), plan.parentSupport(), plan.otherFunds(), plan.monthlyManagementFee(),
                plan.monthlyOtherHousingCost(), existing.createdAt(), existing.updatedAt()));
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
        return assetRepository.save(new BudgetAsset(null, asset.planId(), currentUserId(), asset.assetType(),
                asset.assetName(), asset.estimatedValue(), asset.excluded(), asset.investableAmount(),
                asset.sourceType(), asset.note(), null, null));
    }

    public BudgetAsset updateAsset(BudgetAsset asset) {
        requirePlan(asset.planId());
        final BudgetAsset existing = assetRepository.findById(asset.id()).orElseThrow(NoGroupException::new);
        if (!existing.planId().equals(asset.planId()) || !existing.userId().equals(currentUserId())) {
            throw new NoGroupException();
        }
        return assetRepository.update(new BudgetAsset(existing.id(), existing.planId(), existing.userId(),
                asset.assetType(), asset.assetName(), asset.estimatedValue(), asset.excluded(), asset.investableAmount(),
                asset.sourceType(), asset.note(), existing.createdAt(), existing.updatedAt()));
    }

    public void deleteAsset(Long planId, Long assetId) {
        requirePlan(planId);
        final BudgetAsset existing = assetRepository.findById(assetId).orElseThrow(NoGroupException::new);
        if (!existing.planId().equals(planId) || !existing.userId().equals(currentUserId())) {
            throw new NoGroupException();
        }
        assetRepository.delete(assetId);
    }

    public BudgetItem addItem(BudgetItem item) {
        requirePlan(item.planId());
        return itemRepository.save(item);
    }

    public BudgetItem updateItem(BudgetItem item) {
        final BudgetPlan plan = requirePlan(item.planId());
        final BudgetItem existing = itemRepository.findById(item.id()).orElseThrow(NoGroupException::new);
        if (!plan.id().equals(existing.planId())) {
            throw new NoGroupException();
        }
        final BudgetItem saved = itemRepository.update(item);
        if (plan.scenario() != BudgetScenario.CUSTOM) {
            planRepository.update(withScenario(plan, BudgetScenario.CUSTOM));
        }
        return saved;
    }

    public BudgetPlan applyScenario(Long planId, BudgetScenario scenario) {
        final BudgetPlan plan = requirePlan(planId);
        for (final BudgetItem item : itemRepository.findByPlanId(planId)) {
            itemRepository.update(new BudgetItem(item.id(), item.planId(), item.seedKey(), item.category(),
                    item.itemName(), item.required(), item.recommended(), selectedFor(scenario, item), item.owned(),
                    item.budgetAmountWon(), item.candidateName(), item.candidateUrl(), item.candidatePriceWon(),
                    item.alternativeName(), item.alternativeUrl(), item.alternativePriceWon(), item.candidateFetchStatus(),
                    item.alternativeFetchStatus(), item.note(), item.createdAt(), item.updatedAt()));
        }
        return planRepository.update(withScenario(plan, scenario));
    }

    public BudgetCostEstimate estimateCosts(Long planId) {
        final BudgetPlan plan = requirePlan(planId);
        final BudgetFinancing financing = financingRepository.findByPlanId(planId)
                .orElseGet(() -> emptyFinancing(planId));
        return costEstimator.estimate(plan, financing);
    }

    public BudgetFinancing saveFinancing(BudgetFinancing financing) {
        requirePlan(financing.planId());
        financingRepository.deleteByPlanId(financing.planId());
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

    private Long currentUserId() {
        return accessGuard.currentUser().map(User::id).orElseThrow(NoGroupException::new);
    }

    private BudgetFinancing emptyFinancing(Long planId) {
        return new BudgetFinancing(null, planId, 0L, null, java.math.BigDecimal.ZERO, 360,
                RepaymentType.AMORTIZED, 0L, true, null, null);
    }

    private boolean selectedFor(BudgetScenario scenario, BudgetItem item) {
        return switch (scenario) {
            case MINIMUM -> item.required();
            case RECOMMENDED -> item.recommended();
            case COMFORTABLE -> true;
            case CUSTOM -> item.selected();
        };
    }

    private BudgetPlan withScenario(BudgetPlan plan, BudgetScenario scenario) {
        return new BudgetPlan(plan.id(), plan.groupId(), plan.createdBy(), plan.planName(), scenario,
                plan.selectedPropertyId(), plan.housingType(), plan.region(), plan.houseName(), plan.purchasePrice(),
                plan.exclusiveAreaM2(), plan.contractCash(), plan.balanceCash(), plan.acquisitionTax(), plan.brokerageFee(),
                plan.registrationFee(), plan.movingCost(), plan.cleaningCost(), plan.otherInitialCost(), plan.parentSupport(),
                plan.otherFunds(), plan.monthlyManagementFee(), plan.monthlyOtherHousingCost(), plan.createdAt(), plan.updatedAt());
    }
}
