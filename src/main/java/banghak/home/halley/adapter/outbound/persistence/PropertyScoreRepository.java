package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoreSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.AUTO_SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.CRITERION_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.EFFECTIVE_SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.FALLBACK_REASON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.MANUAL_SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.SCORE_SOURCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class PropertyScoreRepository {

    private final DSLContext dsl;

    public PropertyScoreRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PropertyScore save(PropertyScore score) {
        final Long id = Objects.requireNonNull(dsl.insertInto(TABLE)
                        .set(PROPERTY_ID, score.propertyId())
                        .set(CRITERION_CODE, score.criterionCode())
                        .set(AUTO_SCORE, score.autoScore())
                        .set(MANUAL_SCORE, score.manualScore())
                        .set(EFFECTIVE_SCORE, score.effectiveScore())
                        .set(SCORE_SOURCE, score.scoreSource() == null ? null : score.scoreSource().name())
                        .set(FALLBACK_REASON, score.fallbackReason())
                        .returningResult(ID)
                        .fetchOne())
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<PropertyScore> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<PropertyScore> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    public void deleteByPropertyId(Long propertyId) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .execute();
    }

    public void upsertManualScore(Long propertyId, String criterionCode, BigDecimal manualScore) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, propertyId)
                .set(CRITERION_CODE, criterionCode)
                .set(MANUAL_SCORE, manualScore)
                .onConflict(PROPERTY_ID, CRITERION_CODE)
                .doUpdate()
                .set(MANUAL_SCORE, manualScore)
                .execute();
    }

    private PropertyScore map(Record r) {
        return new PropertyScore(
                r.get(ID),
                r.get(PROPERTY_ID),
                r.get(CRITERION_CODE),
                r.get(AUTO_SCORE),
                r.get(MANUAL_SCORE),
                r.get(EFFECTIVE_SCORE),
                toEnum(ScoreSource.class, r.get(SCORE_SOURCE)),
                r.get(FALLBACK_REASON),
                toInstant(r.get(COMPUTED_AT))
        );
    }
}
