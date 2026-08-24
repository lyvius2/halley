package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class SystemConfigTable {

    private static final String T = "system_config";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> CONFIG_KEY = field(name(T, "config_key"), String.class);
    public static final Field<String> CONFIG_VALUE = field(name(T, "config_value"), String.class);
    public static final Field<String> VALUE_TYPE = field(name(T, "value_type"), String.class);
    public static final Field<String> CATEGORY = field(name(T, "category"), String.class);
    public static final Field<String> DESCRIPTION = field(name(T, "description"), String.class);
    public static final Field<Boolean> MASKED = field(name(T, "masked"), Boolean.class);
    public static final Field<Long> UPDATED_BY = field(name(T, "updated_by"), Long.class);
    public static final Field<OffsetDateTime> UPDATED_AT = field(name(T, "updated_at"), OffsetDateTime.class);

    private SystemConfigTable() {
    }
}
