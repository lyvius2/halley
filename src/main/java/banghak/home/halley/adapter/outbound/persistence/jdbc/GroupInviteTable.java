package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class GroupInviteTable {

    private static final String T = "group_invite";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> CODE = field(name(T, "code"), String.class);
    public static final Field<Long> GROUP_ID = field(name(T, "group_id"), Long.class);
    public static final Field<Long> CREATED_BY = field(name(T, "created_by"), Long.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> EXPIRES_AT = field(name(T, "expires_at"), OffsetDateTime.class);

    private GroupInviteTable() {
    }
}
