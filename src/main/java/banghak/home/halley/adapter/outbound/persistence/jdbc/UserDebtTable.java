package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class UserDebtTable {

    private static final String T = "user_debt";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> USER_ID = field(name(T, "user_id"), Long.class);
    public static final Field<String> DEBT_TYPE = field(name(T, "debt_type"), String.class);
    public static final Field<Long> AMOUNT = field(name(T, "amount"), Long.class);

    private UserDebtTable() {
    }
}
