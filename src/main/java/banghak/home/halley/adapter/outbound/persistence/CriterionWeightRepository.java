package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.scoring.CriterionWeight;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.CRITERION_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.PRIORITY_RANK;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionWeightTable.WEIGHT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class CriterionWeightRepository {

    private static final TypeReference<List<CriterionWeight>> LIST = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ReferenceDataCache cache;

    public CriterionWeightRepository(DSLContext dsl, ReferenceDataCache cache) {
        this.dsl = dsl;
        this.cache = cache;
    }

    public CriterionWeight save(CriterionWeight weight) {
        dsl.insertInto(TABLE)
                .set(CRITERION_CODE, weight.criterionCode())
                .set(PRIORITY_RANK, weight.priorityRank())
                .set(WEIGHT, weight.weight())
                .execute();
        cache.evict(CachePort.CRITERION_WEIGHT);   // 순위가 바뀌면 모든 매물의 총점이 바뀐다 (설계 I239)
        return findById(weight.criterionCode()).orElseThrow();
    }

    public Optional<CriterionWeight> findById(String criterionCode) {
        return dsl.selectFrom(TABLE)
                .where(CRITERION_CODE.eq(criterionCode))
                .fetchOptional()
                .map(this::map);
    }

    /**
     * 총점은 저장하지 않고 <b>읽을 때마다</b> 이 표로 다시 계산합니다 (설계 I173).
     * 그래서 목록을 그릴 때마다 읽힙니다 — 담아 둡니다 (설계 I239).
     */
    public List<CriterionWeight> findAll() {
        return cache.get(CachePort.CRITERION_WEIGHT, ReferenceDataCache.WHOLE, LIST, this::fetchAll);
    }

    private List<CriterionWeight> fetchAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(String criterionCode) {
        dsl.deleteFrom(TABLE)
                .where(CRITERION_CODE.eq(criterionCode))
                .execute();
        cache.evict(CachePort.CRITERION_WEIGHT);
    }

    public void deleteAll() {
        dsl.deleteFrom(TABLE)
                .execute();
        cache.evict(CachePort.CRITERION_WEIGHT);
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
