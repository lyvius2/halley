package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyOpinionTable {

    private static final String T = "property_opinion";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Long> USER_ID = field(name(T, "user_id"), Long.class);
    public static final Field<String> OPINION_TYPE = field(name(T, "opinion_type"), String.class);
    public static final Field<String> CONTENT = field(name(T, "content"), String.class);
    public static final Field<Integer> SORT_ORDER = field(name(T, "sort_order"), Integer.class);

    private PropertyOpinionTable() {
    }
}
