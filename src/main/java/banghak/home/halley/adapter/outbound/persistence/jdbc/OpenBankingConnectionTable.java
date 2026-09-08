package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class OpenBankingConnectionTable {
    private static final String TABLE_NAME = "open_banking_connection";

    public static final Table<Record> TABLE = table(name(TABLE_NAME));
    public static final Field<Long> ID = field(name(TABLE_NAME, "id"), Long.class);
    public static final Field<Long> USER_ID = field(name(TABLE_NAME, "user_id"), Long.class);
    public static final Field<String> PROVIDER = field(name(TABLE_NAME, "provider"), String.class);
    public static final Field<String> ENCRYPTED_ACCESS_TOKEN = field(name(TABLE_NAME, "encrypted_access_token"), String.class);
    public static final Field<String> ENCRYPTED_REFRESH_TOKEN = field(name(TABLE_NAME, "encrypted_refresh_token"), String.class);
    public static final Field<Object> TOKEN_EXPIRES_AT = field(name(TABLE_NAME, "token_expires_at"), Object.class);
    public static final Field<Object> CONSENTED_AT = field(name(TABLE_NAME, "consented_at"), Object.class);
    public static final Field<Object> DISCONNECTED_AT = field(name(TABLE_NAME, "disconnected_at"), Object.class);
    public static final Field<Object> CREATED_AT = field(name(TABLE_NAME, "created_at"), Object.class);
    public static final Field<Object> UPDATED_AT = field(name(TABLE_NAME, "updated_at"), Object.class);

    private OpenBankingConnectionTable() {
    }
}
