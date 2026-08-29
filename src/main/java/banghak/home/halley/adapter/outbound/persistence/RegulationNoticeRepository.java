package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.regulation.RegulationNoticeState;
import banghak.home.halley.domain.regulation.RegulationSeedStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.ANNOUNCED_ON;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.AREA_COUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.MESSAGE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.NOTICE_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.SEED_STATUS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationNoticeTable.ZONE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlDate;

@Repository
public class RegulationNoticeRepository {

    private final DSLContext dsl;

    public RegulationNoticeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public RegulationNoticeState find(RegulationZone zone) {
        return dsl.selectFrom(TABLE).where(ZONE.eq(zone.name())).fetchOptional()
                .map(this::map)
                .orElseGet(() -> RegulationNoticeState.notStarted(zone));
    }

    public List<RegulationNoticeState> findAll() {
        return dsl.selectFrom(TABLE).fetch().map(this::map);
    }

    /** 규제당 한 행만 두므로 있으면 갱신, 없으면 삽입한다. */
    public void save(RegulationNoticeState state) {
        final int updated = dsl.update(TABLE)
                .set(NOTICE_NO, state.noticeNo())
                .set(ANNOUNCED_ON, toSqlDate(state.announcedOn()))
                .set(SEED_STATUS, state.seedStatus().name())
                .set(AREA_COUNT, state.areaCount())
                .set(MESSAGE, state.message())
                .set(UPDATED_AT, toOffset(Instant.now()))
                .where(ZONE.eq(state.zone().name()))
                .execute();
        if (updated == 0) {
            dsl.insertInto(TABLE)
                    .set(ZONE, state.zone().name())
                    .set(NOTICE_NO, state.noticeNo())
                    .set(ANNOUNCED_ON, toSqlDate(state.announcedOn()))
                    .set(SEED_STATUS, state.seedStatus().name())
                    .set(AREA_COUNT, state.areaCount())
                    .set(MESSAGE, state.message())
                    .set(UPDATED_AT, toOffset(Instant.now()))
                    .execute();
        }
    }

    private RegulationNoticeState map(Record r) {
        return new RegulationNoticeState(
                toEnum(RegulationZone.class, r.get(ZONE)),
                r.get(NOTICE_NO),
                toLocalDate(r.get(ANNOUNCED_ON)),
                toEnum(RegulationSeedStatus.class, r.get(SEED_STATUS)),
                r.get(AREA_COUNT) == null ? 0 : r.get(AREA_COUNT),
                r.get(MESSAGE),
                toInstant(r.get(UPDATED_AT)));
    }
}
