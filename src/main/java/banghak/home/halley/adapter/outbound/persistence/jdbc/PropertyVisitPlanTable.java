package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyVisitPlanTable {

    private static final String T = "property_visit_plan";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<LocalDate> VISIT_DATE = field(name(T, "visit_date"), LocalDate.class);
    public static final Field<Long> CREATED_BY = field(name(T, "created_by"), Long.class);
    public static final Field<String> START_ADDRESS = field(name(T, "start_address"), String.class);
    public static final Field<BigDecimal> START_LAT = field(name(T, "start_lat"), BigDecimal.class);
    public static final Field<BigDecimal> START_LNG = field(name(T, "start_lng"), BigDecimal.class);
    public static final Field<String> TRAVEL_MODE = field(name(T, "travel_mode"), String.class);
    public static final Field<LocalTime> WINDOW_START = field(name(T, "window_start"), LocalTime.class);
    public static final Field<LocalTime> WINDOW_END = field(name(T, "window_end"), LocalTime.class);
    public static final Field<Integer> STAY_MINUTES_DEFAULT = field(name(T, "stay_minutes_default"), Integer.class);
    public static final Field<String> STATUS = field(name(T, "status"), String.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private PropertyVisitPlanTable() {
    }
}
