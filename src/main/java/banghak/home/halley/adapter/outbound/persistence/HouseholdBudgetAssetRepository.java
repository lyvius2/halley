package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.budget.AssetType;
import banghak.home.halley.domain.budget.BudgetAsset;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.HouseholdBudgetAssetTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class HouseholdBudgetAssetRepository {
    private final DSLContext dsl;
    public HouseholdBudgetAssetRepository(DSLContext dsl) { this.dsl = dsl; }
    public BudgetAsset save(BudgetAsset asset) {
        final Long id = dsl.insertInto(TABLE).set(PLAN_ID, asset.planId()).set(USER_ID, asset.userId())
                .set(ASSET_TYPE, asset.assetType().name()).set(ASSET_NAME, asset.assetName()).set(ESTIMATED_VALUE, asset.estimatedValue())
                .set(EXCLUDED, asset.excluded()).set(INVESTABLE_AMOUNT, asset.investableAmount()).set(SOURCE_TYPE, asset.sourceType()).set(NOTE, asset.note())
                .returningResult(ID).fetchOne().component1();
        return findById(id).orElseThrow();
    }
    public BudgetAsset update(BudgetAsset asset) {
        dsl.update(TABLE).set(ASSET_TYPE, asset.assetType().name()).set(ASSET_NAME, asset.assetName()).set(ESTIMATED_VALUE, asset.estimatedValue())
                .set(EXCLUDED, asset.excluded()).set(INVESTABLE_AMOUNT, asset.investableAmount()).set(SOURCE_TYPE, asset.sourceType()).set(NOTE, asset.note())
                .where(ID.eq(asset.id())).execute();
        return findById(asset.id()).orElseThrow();
    }
    public Optional<BudgetAsset> findById(Long id) { return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map); }
    public List<BudgetAsset> findByPlanId(Long planId) { return dsl.selectFrom(TABLE).where(PLAN_ID.eq(planId)).orderBy(ID).fetch().map(this::map); }
    public void delete(Long id) { dsl.deleteFrom(TABLE).where(ID.eq(id)).execute(); }
    private BudgetAsset map(Record r) { return new BudgetAsset(r.get(ID), r.get(PLAN_ID), r.get(USER_ID), toEnum(AssetType.class, r.get(ASSET_TYPE)), r.get(ASSET_NAME), r.get(ESTIMATED_VALUE), r.get(EXCLUDED), r.get(INVESTABLE_AMOUNT), r.get(SOURCE_TYPE), r.get(NOTE), toInstant(r.get(CREATED_AT)), toInstant(r.get(UPDATED_AT))); }
}
