package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.PropertyVisitPlanRepository;
import banghak.home.halley.domain.itinerary.PlanStatus;
import banghak.home.halley.domain.itinerary.PropertyVisitPlan;
import banghak.home.halley.domain.itinerary.TravelMode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.CREATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.START_ADDRESS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.START_LAT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.START_LNG;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.STATUS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.STAY_MINUTES_DEFAULT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.TRAVEL_MODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.VISIT_DATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.WINDOW_END;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitPlanTable.WINDOW_START;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalTime;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlTime;

@Repository
public class PropertyVisitPlanJooqRepository implements PropertyVisitPlanRepository {

    private final DSLContext dsl;

    public PropertyVisitPlanJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PropertyVisitPlan save(PropertyVisitPlan plan) {
        Long id = dsl.insertInto(TABLE)
                .set(VISIT_DATE, toSqlDate(plan.visitDate()))
                .set(CREATED_BY, plan.createdBy())
                .set(START_ADDRESS, plan.startAddress())
                .set(START_LAT, plan.startLat())
                .set(START_LNG, plan.startLng())
                .set(TRAVEL_MODE, plan.travelMode() == null ? null : plan.travelMode().name())
                .set(WINDOW_START, toSqlTime(plan.windowStart()))
                .set(WINDOW_END, toSqlTime(plan.windowEnd()))
                .set(STAY_MINUTES_DEFAULT, plan.stayMinutesDefault())
                .set(STATUS, plan.status() == null ? null : plan.status().name())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<PropertyVisitPlan> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<PropertyVisitPlan> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private PropertyVisitPlan map(Record r) {
        return new PropertyVisitPlan(
                r.get(ID),
                toLocalDate(r.get(VISIT_DATE)),
                r.get(CREATED_BY),
                r.get(START_ADDRESS),
                r.get(START_LAT),
                r.get(START_LNG),
                toEnum(TravelMode.class, r.get(TRAVEL_MODE)),
                toLocalTime(r.get(WINDOW_START)),
                toLocalTime(r.get(WINDOW_END)),
                r.get(STAY_MINUTES_DEFAULT),
                toEnum(PlanStatus.class, r.get(STATUS)),
                toInstant(r.get(COMPUTED_AT))
        );
    }
}
