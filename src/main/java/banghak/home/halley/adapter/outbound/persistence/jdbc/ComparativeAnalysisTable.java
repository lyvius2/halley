package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class ComparativeAnalysisTable {

    private static final String T = "comparative_analysis";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    /** `rank`는 SQL 표준 윈도우 함수 이름이라 컬럼명으로 쓰지 않는다. */
    public static final Field<Integer> RANK_NO = field(name(T, "rank_no"), Integer.class);
    public static final Field<BigDecimal> SCORE = field(name(T, "score"), BigDecimal.class);
    public static final Field<String> REASON = field(name(T, "reason"), String.class);
    public static final Field<String> MODEL = field(name(T, "model"), String.class);
    public static final Field<String> BATCH_HASH = field(name(T, "batch_hash"), String.class);
    public static final Field<Integer> PROPERTY_COUNT = field(name(T, "property_count"), Integer.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private ComparativeAnalysisTable() {
    }
}
