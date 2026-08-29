package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.sql.Date;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class RegulatedAreaTable {

    private static final String T = "regulated_area";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> CODE_PREFIX = field(name(T, "code_prefix"), String.class);
    public static final Field<String> ZONE = field(name(T, "zone"), String.class);
    public static final Field<String> AREA_NAME = field(name(T, "area_name"), String.class);
    public static final Field<Date> DESIGNATED_ON = field(name(T, "designated_on"), Date.class);
    public static final Field<Date> RELEASED_ON = field(name(T, "released_on"), Date.class);
    public static final Field<String> NOTE = field(name(T, "note"), String.class);
    public static final Field<OffsetDateTime> UPDATED_AT = field(name(T, "updated_at"), OffsetDateTime.class);

    private RegulatedAreaTable() {
    }
}
