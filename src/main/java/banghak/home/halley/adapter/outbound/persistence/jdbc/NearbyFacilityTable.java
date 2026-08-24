package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class NearbyFacilityTable {

    private static final String T = "nearby_facility";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> CATEGORY = field(name(T, "category"), String.class);
    public static final Field<String> SUB_CATEGORY = field(name(T, "sub_category"), String.class);
    public static final Field<String> NAME = field(name(T, "name"), String.class);
    public static final Field<Integer> DISTANCE_M = field(name(T, "distance_m"), Integer.class);
    public static final Field<Integer> WALK_MINUTES = field(name(T, "walk_minutes"), Integer.class);
    public static final Field<OffsetDateTime> FETCHED_AT = field(name(T, "fetched_at"), OffsetDateTime.class);

    private NearbyFacilityTable() {
    }
}
