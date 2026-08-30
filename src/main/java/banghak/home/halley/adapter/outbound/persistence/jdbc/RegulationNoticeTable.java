package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.sql.Date;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class RegulationNoticeTable {

    private static final String T = "regulation_notice";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> ZONE = field(name(T, "zone"), String.class);
    public static final Field<String> NOTICE_NO = field(name(T, "notice_no"), String.class);
    public static final Field<Date> ANNOUNCED_ON = field(name(T, "announced_on"), Date.class);
    public static final Field<String> SEED_STATUS = field(name(T, "seed_status"), String.class);
    public static final Field<Integer> AREA_COUNT = field(name(T, "area_count"), Integer.class);
    public static final Field<String> MESSAGE = field(name(T, "message"), String.class);
    public static final Field<OffsetDateTime> UPDATED_AT = field(name(T, "updated_at"), OffsetDateTime.class);

    private RegulationNoticeTable() {
    }
}
