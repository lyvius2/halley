package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class MonthlyTradeCacheTable {

    private static final String T = "monthly_trade_cache";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> LAWD_CD = field(name(T, "lawd_cd"), String.class);
    public static final Field<String> DEAL_YM = field(name(T, "deal_ym"), String.class);
    public static final Field<String> DEAL_TYPE = field(name(T, "deal_type"), String.class);
    public static final Field<JSON> PAYLOAD = field(name(T, "payload"), JSON.class);
    /**
     * 읽을 때는 타입을 못 박지 않는다 (설계 I117).
     *
     * <p>같은 컬럼이 live(PostgreSQL)에서는 {@code jsonb}, local(H2)에서는 {@code json}이라
     * 드라이버가 돌려주는 객체가 다릅니다. {@code Field<JSON>}으로 읽으면 live에서
     * {@code JSONB cannot be cast to JSON}으로 터집니다 — 로컬에서는 재현되지 않습니다.
     */
    public static final Field<Object> PAYLOAD_RAW = field(name(T, "payload"), Object.class);
    public static final Field<Integer> TRADE_COUNT = field(name(T, "trade_count"), Integer.class);
    public static final Field<OffsetDateTime> FETCHED_AT = field(name(T, "fetched_at"), OffsetDateTime.class);

    private MonthlyTradeCacheTable() {
    }
}
