package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class AgentTable {

    private static final String T = "agent";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> OFFICE_NAME = field(name(T, "office_name"), String.class);
    public static final Field<String> AGENT_NAME = field(name(T, "agent_name"), String.class);
    public static final Field<String> PHONE = field(name(T, "phone"), String.class);
    public static final Field<String> MOBILE = field(name(T, "mobile"), String.class);
    public static final Field<String> REGISTRATION_NO = field(name(T, "registration_no"), String.class);
    public static final Field<String> ADDRESS = field(name(T, "address"), String.class);
    public static final Field<BigDecimal> LAT = field(name(T, "lat"), BigDecimal.class);
    public static final Field<BigDecimal> LNG = field(name(T, "lng"), BigDecimal.class);

    private AgentTable() {
    }
}
