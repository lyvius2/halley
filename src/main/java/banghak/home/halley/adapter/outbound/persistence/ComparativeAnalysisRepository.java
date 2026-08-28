package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.llm.ComparativeAnalysis;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.BATCH_HASH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.MODEL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.PROPERTY_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.RANK_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.REASON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComparativeAnalysisTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class ComparativeAnalysisRepository {

    private final DSLContext dsl;

    public ComparativeAnalysisRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 매물당 한 건이라 있으면 갱신, 없으면 새로 넣는다. */
    public ComparativeAnalysis upsert(ComparativeAnalysis analysis) {
        final Optional<ComparativeAnalysis> existing = findByPropertyId(analysis.propertyId());
        if (existing.isPresent()) {
            dsl.update(TABLE)
                    .set(RANK_NO, analysis.rankNo())
                    .set(SCORE, analysis.score())
                    .set(REASON, analysis.reason())
                    .set(MODEL, analysis.model())
                    .set(BATCH_HASH, analysis.batchHash())
                    .set(PROPERTY_COUNT, analysis.propertyCount())
                    .set(COMPUTED_AT, toOffset(analysis.computedAt()))
                    .where(ID.eq(existing.get().id()))
                    .execute();
        } else {
            dsl.insertInto(TABLE)
                    .set(PROPERTY_ID, analysis.propertyId())
                    .set(RANK_NO, analysis.rankNo())
                    .set(SCORE, analysis.score())
                    .set(REASON, analysis.reason())
                    .set(MODEL, analysis.model())
                    .set(BATCH_HASH, analysis.batchHash())
                    .set(PROPERTY_COUNT, analysis.propertyCount())
                    .set(COMPUTED_AT, toOffset(analysis.computedAt()))
                    .execute();
        }
        return findByPropertyId(analysis.propertyId()).orElseThrow();
    }

    public Optional<ComparativeAnalysis> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetchOptional()
                .map(this::map);
    }

    /** 순위 오름차순 — 1위가 먼저. */
    public List<ComparativeAnalysis> findAll() {
        return dsl.selectFrom(TABLE)
                .orderBy(RANK_NO.asc().nullsLast(), PROPERTY_ID.asc())
                .fetch()
                .map(this::map);
    }

    public void deleteByPropertyId(Long propertyId) {
        dsl.deleteFrom(TABLE).where(PROPERTY_ID.eq(propertyId)).execute();
    }

    private ComparativeAnalysis map(Record r) {
        return new ComparativeAnalysis(
                r.get(ID), r.get(PROPERTY_ID), r.get(RANK_NO), r.get(SCORE), r.get(REASON),
                r.get(MODEL), r.get(BATCH_HASH), r.get(PROPERTY_COUNT), toInstant(r.get(COMPUTED_AT)));
    }
}
