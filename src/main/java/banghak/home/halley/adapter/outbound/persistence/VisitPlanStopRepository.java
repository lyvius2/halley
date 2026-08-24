package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.itinerary.VisitPlanStop;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.ESTIMATED_ARRIVAL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.ESTIMATED_DEPARTURE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.PLAN_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.STOP_ORDER;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.TRAVEL_MINUTES_FROM_PREV;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.TRAVEL_MODE_SEGMENT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.VISITED;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.VisitPlanStopTable.VISITED_AT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalTime;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlTime;

@Repository
public class VisitPlanStopRepository {

    private final DSLContext dsl;

    public VisitPlanStopRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public VisitPlanStop save(VisitPlanStop stop) {
        Long id = dsl.insertInto(TABLE)
                .set(PLAN_ID, stop.planId())
                .set(PROPERTY_ID, stop.propertyId())
                .set(STOP_ORDER, stop.stopOrder())
                .set(ESTIMATED_ARRIVAL, toSqlTime(stop.estimatedArrival()))
                .set(ESTIMATED_DEPARTURE, toSqlTime(stop.estimatedDeparture()))
                .set(TRAVEL_MINUTES_FROM_PREV, stop.travelMinutesFromPrev())
                .set(TRAVEL_MODE_SEGMENT, stop.travelModeSegment())
                .set(VISITED, stop.visited())
                .set(VISITED_AT, toOffset(stop.visitedAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<VisitPlanStop> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<VisitPlanStop> findByPlanId(Long planId) {
        return dsl.selectFrom(TABLE)
                .where(PLAN_ID.eq(planId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private VisitPlanStop map(Record r) {
        return new VisitPlanStop(
                r.get(ID),
                r.get(PLAN_ID),
                r.get(PROPERTY_ID),
                r.get(STOP_ORDER),
                toLocalTime(r.get(ESTIMATED_ARRIVAL)),
                toLocalTime(r.get(ESTIMATED_DEPARTURE)),
                r.get(TRAVEL_MINUTES_FROM_PREV),
                r.get(TRAVEL_MODE_SEGMENT),
                Boolean.TRUE.equals(r.get(VISITED)),
                toInstant(r.get(VISITED_AT))
        );
    }
}
