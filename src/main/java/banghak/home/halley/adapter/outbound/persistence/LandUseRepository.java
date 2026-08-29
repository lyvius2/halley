package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.CONFLICT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.FETCHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.PNU;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.ZONE_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LandUseTable.ZONE_NAME;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class LandUseRepository {

    private final DSLContext dsl;

    public LandUseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 매물의 토지이용계획을 통째로 갈아 끼운다 — 부분 갱신은 옛 항목이 남아 거짓이 된다. */
    public List<LandUse> replaceAll(Long propertyId, List<LandUse> items) {
        deleteByPropertyId(propertyId);
        for (final LandUse item : items) {
            dsl.insertInto(TABLE)
                    .set(PROPERTY_ID, propertyId)
                    .set(ZONE_CODE, item.zoneCode())
                    .set(ZONE_NAME, item.zoneName())
                    .set(CONFLICT, item.conflict().name())
                    .set(PNU, item.pnu())
                    .set(FETCHED_AT, toOffset(item.fetchedAt()))
                    .execute();
        }
        return findByPropertyId(propertyId);
    }

    /** 포함 → 저촉 → 접함 순. 실제 적용되는 것을 먼저 보여준다. */
    public List<LandUse> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .orderBy(CONFLICT.asc(), ZONE_NAME.asc())
                .fetch()
                .map(this::map);
    }

    public void deleteByPropertyId(Long propertyId) {
        dsl.deleteFrom(TABLE).where(PROPERTY_ID.eq(propertyId)).execute();
    }

    private LandUse map(Record r) {
        return new LandUse(
                r.get(ID), r.get(PROPERTY_ID), r.get(ZONE_CODE), r.get(ZONE_NAME),
                toEnum(LandUseConflict.class, r.get(CONFLICT)), r.get(PNU),
                toInstant(r.get(FETCHED_AT)));
    }
}
