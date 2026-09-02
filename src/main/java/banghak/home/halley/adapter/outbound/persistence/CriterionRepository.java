package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.scoring.Criterion;
import banghak.home.halley.domain.scoring.ScoringType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

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

    private static final TypeReference<List<Criterion>> LIST = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ReferenceDataCache cache;

    public CriterionRepository(DSLContext dsl, ReferenceDataCache cache) {
        this.dsl = dsl;
        this.cache = cache;
    }

    public Criterion save(Criterion criterion) {
        dsl.insertInto(TABLE)
                .set(CODE, criterion.code())
                .set(NAME, criterion.name())
                .set(SCORING_TYPE, criterion.scoringType() == null ? null : criterion.scoringType().name())
                .set(ENABLED, criterion.enabled())
                .execute();
        cache.evict(CachePort.CRITERION);   // 담아 둔 목록이 낡았다 (설계 I239)
        return findById(criterion.code()).orElseThrow();
    }

    public Optional<Criterion> findById(String code) {
        return dsl.selectFrom(TABLE)
                .where(CODE.eq(code))
                .fetchOptional()
                .map(this::map);
    }

    /**
     * 14행짜리 표인데 <b>매물마다</b> 읽히던 자리입니다 (설계 I239).
     * 부르는 곳이 9군데라 부르는 쪽을 하나씩 고치는 대신 여기서 담습니다.
     */
    public List<Criterion> findAll() {
        return cache.get(CachePort.CRITERION, ReferenceDataCache.WHOLE, LIST, this::fetchAll);
    }

    private List<Criterion> fetchAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(String code) {
        dsl.deleteFrom(TABLE)
                .where(CODE.eq(code))
                .execute();
        cache.evict(CachePort.CRITERION);
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
