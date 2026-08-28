package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class LlmRecommendationTable {

    private static final String T = "llm_recommendation";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<BigDecimal> SCORE = field(name(T, "score"), BigDecimal.class);
    public static final Field<String> REASON = field(name(T, "reason"), String.class);
    public static final Field<String> MODEL = field(name(T, "model"), String.class);
    public static final Field<String> PROMPT_HASH = field(name(T, "prompt_hash"), String.class);
    public static final Field<Integer> WORKPLACE_COUNT = field(name(T, "workplace_count"), Integer.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private LlmRecommendationTable() {
    }
}
