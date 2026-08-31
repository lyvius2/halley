package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class LoanEstimateTable {

    private static final String T = "loan_estimate";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> PRODUCT_TYPE = field(name(T, "product_type"), String.class);
    public static final Field<BigDecimal> LTV_RATE = field(name(T, "ltv_rate"), BigDecimal.class);
    public static final Field<Long> LTV_LIMIT = field(name(T, "ltv_limit"), Long.class);
    public static final Field<Long> DSR_LIMIT = field(name(T, "dsr_limit"), Long.class);
    public static final Field<Long> FINAL_LIMIT = field(name(T, "final_limit"), Long.class);
    public static final Field<Long> REQUIRED_CASH = field(name(T, "required_cash"), Long.class);
    public static final Field<Long> ACQUISITION_TAX = field(name(T, "acquisition_tax"), Long.class);
    public static final Field<JSON> ASSUMPTIONS = field(name(T, "assumptions"), JSON.class);
    /**
     * 읽을 때는 타입을 못 박지 않는다 (설계 I117).
     * <p>같은 컬럼이 live(PostgreSQL)에서는 {@code jsonb}, local(H2)에서는 {@code json}이라
     * 드라이버가 돌려주는 객체가 다르다. {@code Field<JSON>}으로 읽으면 live에서
     * {@code JSONB cannot be cast to JSON}으로 터진다 — 로컬에서는 절대 재현되지 않는다.
     * 쓰기는 위 필드를 그대로 쓴다.
     */
    public static final Field<Object> ASSUMPTIONS_RAW = field(name(T, "assumptions"), Object.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private LoanEstimateTable() {
    }
}
