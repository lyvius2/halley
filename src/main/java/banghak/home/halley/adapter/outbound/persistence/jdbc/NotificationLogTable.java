package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class NotificationLogTable {

    private static final String T = "notification_log";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> EVENT_TYPE = field(name(T, "event_type"), String.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> CHANNEL = field(name(T, "channel"), String.class);
    public static final Field<String> STATUS = field(name(T, "status"), String.class);
    public static final Field<Integer> RETRY_COUNT = field(name(T, "retry_count"), Integer.class);
    public static final Field<String> ERROR_MESSAGE = field(name(T, "error_message"), String.class);
    public static final Field<JSON> PAYLOAD = field(name(T, "payload"), JSON.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> SENT_AT = field(name(T, "sent_at"), OffsetDateTime.class);

    private NotificationLogTable() {
    }
}
