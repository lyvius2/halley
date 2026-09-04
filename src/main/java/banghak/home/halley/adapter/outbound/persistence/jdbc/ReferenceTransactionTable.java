package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import org.jooq.impl.SQLDataType;

import java.sql.Date;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class ReferenceTransactionTable {

    private static final String T = "reference_transaction";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> COMPLEX_ID = field(name(T, "complex_id"), Long.class);
    public static final Field<String> DEAL_TYPE = field(name(T, "deal_type"), String.class);
    public static final Field<Date> CONTRACT_DATE = field(name(T, "contract_date"), SQLDataType.DATE);
    public static final Field<Long> PRICE = field(name(T, "price"), Long.class);
    public static final Field<BigDecimal> AREA_M2 = field(name(T, "area_m2"), BigDecimal.class);
    public static final Field<Integer> FLOOR_NO = field(name(T, "floor_no"), Integer.class);
    public static final Field<String> SOURCE = field(name(T, "source"), String.class);
    public static final Field<OffsetDateTime> CACHED_AT = field(name(T, "cached_at"), OffsetDateTime.class);

    private ReferenceTransactionTable() {
    }
}
