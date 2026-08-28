package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class UserTable {

    private static final String T = "users";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> NICKNAME = field(name(T, "nickname"), String.class);
    public static final Field<String> LOGIN_ID = field(name(T, "login_id"), String.class);
    public static final Field<String> EMAIL = field(name(T, "email"), String.class);
    public static final Field<String> PASSWORD_HASH = field(name(T, "password_hash"), String.class);
    public static final Field<String> ROLE = field(name(T, "role"), String.class);
    public static final Field<String> WORKPLACE_NAME = field(name(T, "workplace_name"), String.class);
    public static final Field<BigDecimal> WORKPLACE_LAT = field(name(T, "workplace_lat"), BigDecimal.class);
    public static final Field<BigDecimal> WORKPLACE_LNG = field(name(T, "workplace_lng"), BigDecimal.class);
    public static final Field<Boolean> MUST_CHANGE_PASSWORD = field(name(T, "must_change_password"), Boolean.class);
    public static final Field<Long> AVAILABLE_BUDGET = field(name(T, "available_budget"), Long.class);
    public static final Field<Boolean> ENABLED = field(name(T, "enabled"), Boolean.class);
    public static final Field<OffsetDateTime> DISABLED_AT = field(name(T, "disabled_at"), OffsetDateTime.class);
    public static final Field<Long> DISABLED_BY = field(name(T, "disabled_by"), Long.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);

    private UserTable() {
    }
}
