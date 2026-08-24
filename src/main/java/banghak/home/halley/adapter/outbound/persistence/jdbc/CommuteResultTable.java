package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class CommuteResultTable {

    private static final String T = "commute_result";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Long> USER_ID = field(name(T, "user_id"), Long.class);
    public static final Field<Integer> TOTAL_MINUTES = field(name(T, "total_minutes"), Integer.class);
    public static final Field<Integer> TRANSFER_COUNT = field(name(T, "transfer_count"), Integer.class);
    public static final Field<Integer> WALK_MINUTES = field(name(T, "walk_minutes"), Integer.class);
    public static final Field<JSON> PATH_SUMMARY = field(name(T, "path_summary"), JSON.class);
    public static final Field<OffsetDateTime> FETCHED_AT = field(name(T, "fetched_at"), OffsetDateTime.class);

    private CommuteResultTable() {
    }
}
