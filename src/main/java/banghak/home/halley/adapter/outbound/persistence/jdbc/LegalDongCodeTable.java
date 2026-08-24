package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class LegalDongCodeTable {

    private static final String T = "legal_dong_code";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> CODE = field(name(T, "code"), String.class);
    public static final Field<String> SIDO = field(name(T, "sido"), String.class);
    public static final Field<String> SIGUNGU = field(name(T, "sigungu"), String.class);
    public static final Field<String> DONG_NAME = field(name(T, "dong_name"), String.class);
    public static final Field<String> RI_NAME = field(name(T, "ri_name"), String.class);
    public static final Field<Boolean> IS_ACTIVE = field(name(T, "is_active"), Boolean.class);
    public static final Field<OffsetDateTime> UPDATED_AT = field(name(T, "updated_at"), OffsetDateTime.class);

    private LegalDongCodeTable() {
    }
}
