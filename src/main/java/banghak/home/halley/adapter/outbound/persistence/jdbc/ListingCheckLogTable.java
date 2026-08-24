package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class ListingCheckLogTable {

    private static final String T = "listing_check_log";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<OffsetDateTime> CHECKED_AT = field(name(T, "checked_at"), OffsetDateTime.class);
    public static final Field<Integer> HTTP_STATUS = field(name(T, "http_status"), Integer.class);
    public static final Field<String> VERDICT = field(name(T, "verdict"), String.class);
    public static final Field<String> EVIDENCE = field(name(T, "evidence"), String.class);
    public static final Field<Integer> ELAPSED_MS = field(name(T, "elapsed_ms"), Integer.class);
    public static final Field<Boolean> NOTIFIED = field(name(T, "notified"), Boolean.class);

    private ListingCheckLogTable() {
    }
}
