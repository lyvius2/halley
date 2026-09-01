package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyVisitTable {

    private static final String T = "property_visit";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Long> USER_ID = field(name(T, "user_id"), Long.class);
    public static final Field<OffsetDateTime> VISITED_AT = field(name(T, "visited_at"), OffsetDateTime.class);

    private PropertyVisitTable() {
    }
}
