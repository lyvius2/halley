package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationZone;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.AREA_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.CODE_PREFIX;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.DESIGNATED_ON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.NOTE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.RELEASED_ON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulatedAreaTable.ZONE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlDate;

@Repository
public class RegulatedAreaRepository {

    private final DSLContext dsl;

    public RegulatedAreaRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public RegulatedArea save(RegulatedArea area) {
        final Long id = dsl.insertInto(TABLE)
                .set(CODE_PREFIX, area.codePrefix())
                .set(ZONE, area.zone().name())
                .set(AREA_NAME, area.areaName())
                .set(DESIGNATED_ON, toSqlDate(area.designatedOn()))
                .set(RELEASED_ON, toSqlDate(area.releasedOn()))
                .set(NOTE, area.note())
                .set(UPDATED_AT, toOffset(area.updatedAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<RegulatedArea> findById(Long id) {
        return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map);
    }

    /**
     * 법정동코드 앞자리가 일치하는 지정을 찾는다. 10자리(법정동)와 5자리(시군구) 후보를 모두 본다.
     */
    public List<RegulatedArea> findByCodePrefixes(List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(TABLE)
                .where(CODE_PREFIX.in(prefixes))
                .fetch()
                .map(this::map);
    }

    public List<RegulatedArea> findAll() {
        return dsl.selectFrom(TABLE)
                .orderBy(CODE_PREFIX.asc(), DESIGNATED_ON.desc().nullsLast())
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute();
    }

    private RegulatedArea map(Record r) {
        return new RegulatedArea(
                r.get(ID), r.get(CODE_PREFIX), toEnum(RegulationZone.class, r.get(ZONE)),
                r.get(AREA_NAME), toLocalDate(r.get(DESIGNATED_ON)), toLocalDate(r.get(RELEASED_ON)),
                r.get(NOTE), toInstant(r.get(UPDATED_AT)));
    }
}
