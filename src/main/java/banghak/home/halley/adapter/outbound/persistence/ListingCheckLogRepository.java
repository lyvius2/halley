package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ListingCheckLog;
import banghak.home.halley.domain.property.ListingVerdict;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.CHECKED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.ELAPSED_MS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.EVIDENCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.HTTP_STATUS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.NOTIFIED;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ListingCheckLogTable.VERDICT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class ListingCheckLogRepository {

    private final DSLContext dsl;

    public ListingCheckLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ListingCheckLog save(ListingCheckLog log) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, log.propertyId())
                .set(HTTP_STATUS, log.httpStatus())
                .set(VERDICT, log.verdict() == null ? null : log.verdict().name())
                .set(EVIDENCE, log.evidence())
                .set(ELAPSED_MS, log.elapsedMs())
                .set(NOTIFIED, log.notified())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<ListingCheckLog> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<ListingCheckLog> findByPropertyId(Long propertyId) {
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

    private ListingCheckLog map(Record r) {
        return new ListingCheckLog(
                r.get(ID),
                r.get(PROPERTY_ID),
                toInstant(r.get(CHECKED_AT)),
                r.get(HTTP_STATUS),
                toEnum(ListingVerdict.class, r.get(VERDICT)),
                r.get(EVIDENCE),
                r.get(ELAPSED_MS),
                Boolean.TRUE.equals(r.get(NOTIFIED))
        );
    }
}
