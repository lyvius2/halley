package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.NearbyFacility;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.CATEGORY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.DISTANCE_M;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.FETCHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.SUB_CATEGORY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.NearbyFacilityTable.WALK_MINUTES;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class NearbyFacilityRepository {

    private final DSLContext dsl;

    public NearbyFacilityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public NearbyFacility save(NearbyFacility facility) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, facility.propertyId())
                .set(CATEGORY, facility.category())
                .set(SUB_CATEGORY, facility.subCategory())
                .set(NAME, facility.name())
                .set(DISTANCE_M, facility.distanceM())
                .set(WALK_MINUTES, facility.walkMinutes())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<NearbyFacility> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<NearbyFacility> findByPropertyId(Long propertyId) {
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

    private NearbyFacility map(Record r) {
        return new NearbyFacility(
                r.get(ID),
                r.get(PROPERTY_ID),
                r.get(CATEGORY),
                r.get(SUB_CATEGORY),
                r.get(NAME),
                r.get(DISTANCE_M),
                r.get(WALK_MINUTES),
                toInstant(r.get(FETCHED_AT))
        );
    }
}
