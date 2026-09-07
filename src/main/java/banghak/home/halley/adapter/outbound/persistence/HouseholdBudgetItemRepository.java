package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.budget.BudgetItem;
import banghak.home.halley.domain.budget.ProductFetchStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.HouseholdBudgetItemTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class HouseholdBudgetItemRepository {
    private final DSLContext dsl;
    public HouseholdBudgetItemRepository(DSLContext dsl) { this.dsl = dsl; }

    public BudgetItem save(BudgetItem item) {
        final Long id = dsl.insertInto(TABLE).set(PLAN_ID, item.planId()).set(SEED_KEY, item.seedKey()).set(CATEGORY, item.category()).set(ITEM_NAME, item.itemName()).set(REQUIRED, item.required()).set(RECOMMENDED, item.recommended()).set(SELECTED, item.selected()).set(OWNED, item.owned()).set(BUDGET, item.budgetAmountWon()).set(CANDIDATE_NAME, item.candidateName()).set(CANDIDATE_URL, item.candidateUrl()).set(CANDIDATE_PRICE, item.candidatePriceWon()).set(ALTERNATIVE_NAME, item.alternativeName()).set(ALTERNATIVE_URL, item.alternativeUrl()).set(ALTERNATIVE_PRICE, item.alternativePriceWon()).set(CANDIDATE_STATUS, item.candidateFetchStatus().name()).set(ALTERNATIVE_STATUS, item.alternativeFetchStatus().name()).set(NOTE, item.note()).returningResult(ID).fetchOne().component1();
        return findById(id).orElseThrow();
    }

    public BudgetItem update(BudgetItem item) {
        dsl.update(TABLE).set(SELECTED, item.selected()).set(OWNED, item.owned()).set(BUDGET, item.budgetAmountWon())
                .set(CANDIDATE_NAME, item.candidateName()).set(CANDIDATE_URL, item.candidateUrl()).set(CANDIDATE_PRICE, item.candidatePriceWon())
                .set(ALTERNATIVE_NAME, item.alternativeName()).set(ALTERNATIVE_URL, item.alternativeUrl()).set(ALTERNATIVE_PRICE, item.alternativePriceWon())
                .set(CANDIDATE_STATUS, item.candidateFetchStatus().name()).set(ALTERNATIVE_STATUS, item.alternativeFetchStatus().name()).set(NOTE, item.note())
                .where(ID.eq(item.id())).execute();
        return findById(item.id()).orElseThrow();
    }

    public Optional<BudgetItem> findById(Long id) { return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map); }
    public List<BudgetItem> findByPlanId(Long planId) { return dsl.selectFrom(TABLE).where(PLAN_ID.eq(planId)).orderBy(ID).fetch().map(this::map); }
    public void delete(Long id) { dsl.deleteFrom(TABLE).where(ID.eq(id)).execute(); }

    private BudgetItem map(Record r) {
        return new BudgetItem(r.get(ID), r.get(PLAN_ID), r.get(SEED_KEY), r.get(CATEGORY), r.get(ITEM_NAME), r.get(REQUIRED), r.get(RECOMMENDED), r.get(SELECTED), r.get(OWNED), r.get(BUDGET), r.get(CANDIDATE_NAME), r.get(CANDIDATE_URL), r.get(CANDIDATE_PRICE), r.get(ALTERNATIVE_NAME), r.get(ALTERNATIVE_URL), r.get(ALTERNATIVE_PRICE), toEnum(ProductFetchStatus.class, r.get(CANDIDATE_STATUS)), toEnum(ProductFetchStatus.class, r.get(ALTERNATIVE_STATUS)), r.get(NOTE), toInstant(r.get(CREATED_AT)), toInstant(r.get(UPDATED_AT)));
    }
}
