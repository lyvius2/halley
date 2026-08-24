package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.ScoringType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionTable.CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionTable.ENABLED;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionTable.NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionTable.SCORING_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.CriterionTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;

@Repository
public class CriterionRepository {

    private final DSLContext dsl;

    public CriterionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Criterion save(Criterion criterion) {
        dsl.insertInto(TABLE)
                .set(CODE, criterion.code())
                .set(NAME, criterion.name())
                .set(SCORING_TYPE, criterion.scoringType() == null ? null : criterion.scoringType().name())
                .set(ENABLED, criterion.enabled())
                .execute();
        return findById(criterion.code()).orElseThrow();
    }

    public Optional<Criterion> findById(String code) {
        return dsl.selectFrom(TABLE)
                .where(CODE.eq(code))
                .fetchOptional()
                .map(this::map);
    }

    public List<Criterion> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(String code) {
        dsl.deleteFrom(TABLE)
                .where(CODE.eq(code))
                .execute();
    }

    private Criterion map(Record r) {
        return new Criterion(
                r.get(CODE),
                r.get(NAME),
                toEnum(ScoringType.class, r.get(SCORING_TYPE)),
                Boolean.TRUE.equals(r.get(ENABLED))
        );
    }
}
