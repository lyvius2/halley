package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.llm.LlmRecommendation;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.MODEL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.PROMPT_HASH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.REASON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LlmRecommendationTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class LlmRecommendationRepository {

    private final DSLContext dsl;

    public LlmRecommendationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 매물당 한 건이라 있으면 갱신, 없으면 새로 넣는다. */
    public LlmRecommendation upsert(LlmRecommendation recommendation) {
        final Optional<LlmRecommendation> existing = findByPropertyId(recommendation.propertyId());
        if (existing.isPresent()) {
            dsl.update(TABLE)
                    .set(SCORE, recommendation.score())
                    .set(REASON, recommendation.reason())
                    .set(MODEL, recommendation.model())
                    .set(PROMPT_HASH, recommendation.promptHash())
                    .set(COMPUTED_AT, toOffset(recommendation.computedAt()))
                    .where(ID.eq(existing.get().id()))
                    .execute();
            return findByPropertyId(recommendation.propertyId()).orElseThrow();
        }
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, recommendation.propertyId())
                .set(SCORE, recommendation.score())
                .set(REASON, recommendation.reason())
                .set(MODEL, recommendation.model())
                .set(PROMPT_HASH, recommendation.promptHash())
                .set(COMPUTED_AT, toOffset(recommendation.computedAt()))
                .execute();
        return findByPropertyId(recommendation.propertyId()).orElseThrow();
    }

    public Optional<LlmRecommendation> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetchOptional()
                .map(this::map);
    }

    public List<LlmRecommendation> findAll() {
        return dsl.selectFrom(TABLE).fetch().map(this::map);
    }

    public void deleteByPropertyId(Long propertyId) {
        dsl.deleteFrom(TABLE).where(PROPERTY_ID.eq(propertyId)).execute();
    }

    private LlmRecommendation map(Record r) {
        return new LlmRecommendation(
                r.get(ID), r.get(PROPERTY_ID), r.get(SCORE), r.get(REASON),
                r.get(MODEL), r.get(PROMPT_HASH), toInstant(r.get(COMPUTED_AT)));
    }
}
