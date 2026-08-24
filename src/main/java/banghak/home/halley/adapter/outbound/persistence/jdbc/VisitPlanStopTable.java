package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.sql.Time;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class VisitPlanStopTable {

    private static final String T = "visit_plan_stop";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PLAN_ID = field(name(T, "plan_id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Integer> STOP_ORDER = field(name(T, "stop_order"), Integer.class);
    public static final Field<Time> ESTIMATED_ARRIVAL = field(name(T, "estimated_arrival"), SQLDataType.TIME);
    public static final Field<Time> ESTIMATED_DEPARTURE = field(name(T, "estimated_departure"), SQLDataType.TIME);
    public static final Field<Integer> TRAVEL_MINUTES_FROM_PREV = field(name(T, "travel_minutes_from_prev"), Integer.class);
    public static final Field<String> TRAVEL_MODE_SEGMENT = field(name(T, "travel_mode_segment"), String.class);
    public static final Field<Boolean> VISITED = field(name(T, "visited"), Boolean.class);
    public static final Field<OffsetDateTime> VISITED_AT = field(name(T, "visited_at"), OffsetDateTime.class);

    private VisitPlanStopTable() {
    }
}
