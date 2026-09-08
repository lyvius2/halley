package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class OpenBankingSyncLogTable {
    private static final String TABLE_NAME = "open_banking_sync_log";

    public static final Table<Record> TABLE = table(name(TABLE_NAME));
    public static final Field<Long> ID = field(name(TABLE_NAME, "id"), Long.class);
    public static final Field<Long> CONNECTION_ID = field(name(TABLE_NAME, "connection_id"), Long.class);
    public static final Field<Object> REQUESTED_AT = field(name(TABLE_NAME, "requested_at"), Object.class);
    public static final Field<Object> COMPLETED_AT = field(name(TABLE_NAME, "completed_at"), Object.class);
    public static final Field<String> STATUS = field(name(TABLE_NAME, "status"), String.class);
    public static final Field<String> FAILURE_CODE = field(name(TABLE_NAME, "failure_code"), String.class);
    public static final Field<Integer> ACCOUNT_COUNT = field(name(TABLE_NAME, "account_count"), Integer.class);
    public static final Field<Object> CREATED_AT = field(name(TABLE_NAME, "created_at"), Object.class);

    private OpenBankingSyncLogTable() {
    }
}
