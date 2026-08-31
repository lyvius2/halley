package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.scoring.PropertyScore;
import banghak.home.halley.domain.scoring.ScoreSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.AUTO_SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.CRITERION_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.EFFECTIVE_SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyScoreTable.EXPLANATION;
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
                .set(EXPLANATION, score.explanation())
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

    /**
     * 매물 여러 건을 한 번에 (설계 I124).
     *
     * <p>목록 화면이 매물마다 따로 부르면 그 수만큼 왕복이 늘어납니다 — 느린 DB에서는
     * 그것이 그대로 체감 지연이 됩니다. 비어 있으면 질의하지 않습니다:
     * {@code IN ()} 는 dialect마다 다르게 굴어 굳이 시험할 이유가 없습니다.
     */
    public List<PropertyScore> findByPropertyIds(java.util.Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.in(propertyIds))
                .fetch()
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

    /**
     * 한 매물의 채점을 통째로 갈아 끼운다 (설계 I84).
     *
     * <p><b>지우고 다시 넣지 않습니다.</b> 예전에는 `deleteByPropertyId` 뒤에 전부 insert 했는데,
     * 등록 시점 채점과 비동기 보정의 재채점이 겹치면 유니크 제약에 걸려 터졌습니다. 항목마다
     * upsert 하면 순서가 어떻게 되든 마지막 값이 남고 충돌하지 않습니다.
     *
     * <p>사라진 항목만 지웁니다 — 채점 기준이 빠졌을 때 옛 점수가 남지 않게 합니다.
     */
    public void replaceAll(Long propertyId, List<PropertyScore> scores) {
        for (final PropertyScore score : scores) {
            dsl.insertInto(TABLE)
                    .set(PROPERTY_ID, propertyId)
                    .set(CRITERION_CODE, score.criterionCode())
                    .set(AUTO_SCORE, score.autoScore())
                    .set(MANUAL_SCORE, score.manualScore())
                    .set(EFFECTIVE_SCORE, score.effectiveScore())
                    .set(SCORE_SOURCE, score.scoreSource() == null ? null : score.scoreSource().name())
                    .set(FALLBACK_REASON, score.fallbackReason())
                    .set(EXPLANATION, score.explanation())
                    .onConflict(PROPERTY_ID, CRITERION_CODE)
                    .doUpdate()
                    .set(AUTO_SCORE, score.autoScore())
                    .set(MANUAL_SCORE, score.manualScore())
                    .set(EFFECTIVE_SCORE, score.effectiveScore())
                    .set(SCORE_SOURCE, score.scoreSource() == null ? null : score.scoreSource().name())
                    .set(FALLBACK_REASON, score.fallbackReason())
                    .set(EXPLANATION, score.explanation())
                    .execute();
        }
        final List<String> keep = scores.stream().map(PropertyScore::criterionCode).toList();
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .and(keep.isEmpty() ? DSL.trueCondition() : CRITERION_CODE.notIn(keep))
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
                r.get(EXPLANATION),
                toInstant(r.get(COMPUTED_AT))
        );
    }
}
