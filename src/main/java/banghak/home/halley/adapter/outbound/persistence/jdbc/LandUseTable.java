package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class LandUseTable {

    private static final String T = "land_use";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> ZONE_CODE = field(name(T, "zone_code"), String.class);
    public static final Field<String> ZONE_NAME = field(name(T, "zone_name"), String.class);
    public static final Field<String> CONFLICT = field(name(T, "conflict"), String.class);
    public static final Field<String> PNU = field(name(T, "pnu"), String.class);
    public static final Field<OffsetDateTime> FETCHED_AT = field(name(T, "fetched_at"), OffsetDateTime.class);

    private LandUseTable() {
    }
}
