package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class UserGroupTable {

    private static final String T = "user_group";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> NAME = field(name(T, "name"), String.class);
    public static final Field<Long> CREATED_BY = field(name(T, "created_by"), Long.class);
    public static final Field<String> SLACK_WEBHOOK_URL =
            field(name(T, "slack_webhook_url"), String.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);

    private UserGroupTable() {
    }
}
