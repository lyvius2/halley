package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class ComplexTable {

    private static final String T = "complex";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> MATCH_KEY = field(name(T, "match_key"), String.class);
    public static final Field<String> NAME = field(name(T, "name"), String.class);
    public static final Field<String> ADDRESS_JIBUN = field(name(T, "address_jibun"), String.class);
    public static final Field<BigDecimal> LAT = field(name(T, "lat"), BigDecimal.class);
    public static final Field<BigDecimal> LNG = field(name(T, "lng"), BigDecimal.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name(T, "created_at"), OffsetDateTime.class);

    private ComplexTable() {
    }
}
