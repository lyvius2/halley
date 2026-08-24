package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyAgentTable {

    private static final String T = "property_agent";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Long> AGENT_ID = field(name(T, "agent_id"), Long.class);
    public static final Field<Boolean> IS_PRIMARY = field(name(T, "is_primary"), Boolean.class);

    private PropertyAgentTable() {
    }
}
