package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PriceForecastHistoryTable {

    private static final String T = "price_forecast_history";

    public static final Table<Record> TABLE = table(name(T));
    /** {@code PriceForecastTable.TABLE}과 한 파일에서 같이 쓰이므로 이름을 겹치지 않게 둔다. */
    public static final Table<Record> TABLE_HISTORY = TABLE;

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> DIRECTION = field(name(T, "direction"), String.class);
    public static final Field<String> CODE_DIRECTION = field(name(T, "code_direction"), String.class);
    public static final Field<String> CONFIDENCE = field(name(T, "confidence"), String.class);
    public static final Field<Integer> HORIZON_MONTHS = field(name(T, "horizon_months"), Integer.class);
    public static final Field<JSON> FACTORS = field(name(T, "factors"), JSON.class);
    public static final Field<JSON> CAVEATS = field(name(T, "caveats"), JSON.class);
    /** 읽을 때는 타입을 못 박지 않는다 (설계 I117) — live는 jsonb, local은 json. */
    public static final Field<Object> FACTORS_RAW = field(name(T, "factors"), Object.class);
    public static final Field<Object> CAVEATS_RAW = field(name(T, "caveats"), Object.class);
    public static final Field<String> MODEL = field(name(T, "model"), String.class);
    public static final Field<String> PROMPT_HASH = field(name(T, "prompt_hash"), String.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private PriceForecastHistoryTable() {
    }
}
