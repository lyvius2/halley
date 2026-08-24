package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.CriterionWeightRepository;
import banghak.home.halley.domain.scoring.CriterionWeight;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.CRITERION_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.PRIORITY_RANK;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.WEIGHT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class CriterionWeightJooqRepository implements CriterionWeightRepository {

    private final DSLContext dsl;

    public CriterionWeightJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public CriterionWeight save(CriterionWeight weight) {
        dsl.insertInto(TABLE)
                .set(CRITERION_CODE, weight.criterionCode())
                .set(PRIORITY_RANK, weight.priorityRank())
                .set(WEIGHT, weight.weight())
                .execute();
        return findById(weight.criterionCode()).orElseThrow();
    }

    @Override
    public Optional<CriterionWeight> findById(String criterionCode) {
        return dsl.selectFrom(TABLE)
                .where(CRITERION_CODE.eq(criterionCode))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<CriterionWeight> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(String criterionCode) {
        dsl.deleteFrom(TABLE)
                .where(CRITERION_CODE.eq(criterionCode))
                .execute();
    }

    private CriterionWeight map(Record r) {
        return new CriterionWeight(
                r.get(CRITERION_CODE),
                r.get(PRIORITY_RANK),
                r.get(WEIGHT),
                toInstant(r.get(UPDATED_AT))
        );
    }
}
