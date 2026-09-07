package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.itinerary.PropertyVisit;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.VISITED_AT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class PropertyVisitRepository {

    private final DSLContext dsl;

    public PropertyVisitRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

 /** 체크한다. */
    public void mark(Long propertyId, Long userId, Instant visitedAt) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, propertyId)
                .set(USER_ID, userId)
                .set(VISITED_AT, toOffset(visitedAt))
                .onConflict(PROPERTY_ID, USER_ID)
                .doNothing()
                .execute();
    }

 /** 체크를 푼다. 잘못 눌렀을 때다. 행을 지운다. */
    public void clear(Long propertyId, Long userId) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)))
                .execute();
    }

 /** dsl.fetchExists 를 쓰지 마십시오. */
    public boolean exists(Long propertyId, Long userId) {
        return dsl.fetchCount(TABLE, PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId))) > 0;
    }

 /** 이 사람이 가 본 곳 전부. 화면이 체크 상태를 되살릴 때 쓴다. */
    public List<PropertyVisit> findByUser(Long userId) {
        return dsl.selectFrom(TABLE)
                .where(USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

    private PropertyVisit map(Record r) {
        return new PropertyVisit(
                r.get(ID),
                r.get(PROPERTY_ID),
                r.get(USER_ID),
                toInstant(r.get(VISITED_AT)));
    }
}
