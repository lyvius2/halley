package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.budget.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.HouseholdBudgetPlanTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class HouseholdBudgetPlanRepository {
    private final DSLContext dsl;
    public HouseholdBudgetPlanRepository(DSLContext dsl) { this.dsl = dsl; }

    public BudgetPlan save(BudgetPlan plan) {
        final Long id = dsl.insertInto(TABLE).set(GROUP_ID, plan.groupId()).set(CREATED_BY, plan.createdBy())
                .set(PLAN_NAME, plan.planName()).set(SCENARIO, plan.scenario().name()).set(SELECTED_PROPERTY_ID, plan.selectedPropertyId())
                .set(HOUSING_TYPE, plan.housingType().name()).set(REGION, plan.region()).set(HOUSE_NAME, plan.houseName())
                .set(PURCHASE_PRICE, plan.purchasePrice()).set(EXCLUSIVE_AREA, plan.exclusiveAreaM2()).set(CONTRACT_CASH, plan.contractCash())
                .set(BALANCE_CASH, plan.balanceCash()).set(ACQUISITION_TAX, plan.acquisitionTax()).set(BROKERAGE_FEE, plan.brokerageFee())
                .set(REGISTRATION_FEE, plan.registrationFee()).set(MOVING_COST, plan.movingCost()).set(CLEANING_COST, plan.cleaningCost())
                .set(ACQUISITION_TAX_SOURCE, plan.acquisitionTaxSource().name()).set(BROKERAGE_FEE_SOURCE, plan.brokerageFeeSource().name())
                .set(REGISTRATION_FEE_SOURCE, plan.registrationFeeSource().name()).set(MOVING_COST_SOURCE, plan.movingCostSource().name())
                .set(CLEANING_COST_SOURCE, plan.cleaningCostSource().name())
                .set(OTHER_INITIAL_COST, plan.otherInitialCost()).set(PARENT_SUPPORT, plan.parentSupport()).set(OTHER_FUNDS, plan.otherFunds())
                .set(MONTHLY_MANAGEMENT_FEE, plan.monthlyManagementFee()).set(MONTHLY_OTHER_HOUSING_COST, plan.monthlyOtherHousingCost())
                .returningResult(ID).fetchOne().component1();
        return findById(id).orElseThrow();
    }

    public BudgetPlan update(BudgetPlan plan) {
        dsl.update(TABLE).set(PLAN_NAME, plan.planName()).set(SCENARIO, plan.scenario().name())
                .set(SELECTED_PROPERTY_ID, plan.selectedPropertyId()).set(HOUSING_TYPE, plan.housingType().name())
                .set(REGION, plan.region()).set(HOUSE_NAME, plan.houseName()).set(PURCHASE_PRICE, plan.purchasePrice())
                .set(EXCLUSIVE_AREA, plan.exclusiveAreaM2()).set(CONTRACT_CASH, plan.contractCash()).set(BALANCE_CASH, plan.balanceCash())
                .set(ACQUISITION_TAX, plan.acquisitionTax()).set(BROKERAGE_FEE, plan.brokerageFee()).set(REGISTRATION_FEE, plan.registrationFee())
                .set(MOVING_COST, plan.movingCost()).set(CLEANING_COST, plan.cleaningCost()).set(OTHER_INITIAL_COST, plan.otherInitialCost())
                .set(ACQUISITION_TAX_SOURCE, plan.acquisitionTaxSource().name()).set(BROKERAGE_FEE_SOURCE, plan.brokerageFeeSource().name())
                .set(REGISTRATION_FEE_SOURCE, plan.registrationFeeSource().name()).set(MOVING_COST_SOURCE, plan.movingCostSource().name())
                .set(CLEANING_COST_SOURCE, plan.cleaningCostSource().name())
                .set(PARENT_SUPPORT, plan.parentSupport()).set(OTHER_FUNDS, plan.otherFunds()).set(MONTHLY_MANAGEMENT_FEE, plan.monthlyManagementFee())
                .set(MONTHLY_OTHER_HOUSING_COST, plan.monthlyOtherHousingCost()).where(ID.eq(plan.id())).execute();
        return findById(plan.id()).orElseThrow();
    }

    public Optional<BudgetPlan> findById(Long id) { return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map); }
    public List<BudgetPlan> findByGroupId(Long groupId) { return dsl.selectFrom(TABLE).where(GROUP_ID.eq(groupId)).orderBy(UPDATED_AT.desc()).fetch().map(this::map); }
    public void delete(Long id) { dsl.deleteFrom(TABLE).where(ID.eq(id)).execute(); }

    private BudgetPlan map(Record r) {
        return new BudgetPlan(r.get(ID), r.get(GROUP_ID), r.get(CREATED_BY), r.get(PLAN_NAME), toEnum(BudgetScenario.class, r.get(SCENARIO)), r.get(SELECTED_PROPERTY_ID), toEnum(HousingType.class, r.get(HOUSING_TYPE)), r.get(REGION), r.get(HOUSE_NAME), r.get(PURCHASE_PRICE), r.get(EXCLUSIVE_AREA), r.get(CONTRACT_CASH), r.get(BALANCE_CASH), r.get(ACQUISITION_TAX), r.get(BROKERAGE_FEE), r.get(REGISTRATION_FEE), r.get(MOVING_COST), r.get(CLEANING_COST), toEnum(CostInputSource.class, r.get(ACQUISITION_TAX_SOURCE)), toEnum(CostInputSource.class, r.get(BROKERAGE_FEE_SOURCE)), toEnum(CostInputSource.class, r.get(REGISTRATION_FEE_SOURCE)), toEnum(CostInputSource.class, r.get(MOVING_COST_SOURCE)), toEnum(CostInputSource.class, r.get(CLEANING_COST_SOURCE)), r.get(OTHER_INITIAL_COST), r.get(PARENT_SUPPORT), r.get(OTHER_FUNDS), r.get(MONTHLY_MANAGEMENT_FEE), r.get(MONTHLY_OTHER_HOUSING_COST), toInstant(r.get(CREATED_AT)), toInstant(r.get(UPDATED_AT)));
    }
}
