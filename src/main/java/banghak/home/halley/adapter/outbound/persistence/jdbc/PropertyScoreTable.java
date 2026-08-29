package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class PropertyScoreTable {

    private static final String T = "property_score";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<String> CRITERION_CODE = field(name(T, "criterion_code"), String.class);
    public static final Field<BigDecimal> AUTO_SCORE = field(name(T, "auto_score"), BigDecimal.class);
    public static final Field<BigDecimal> MANUAL_SCORE = field(name(T, "manual_score"), BigDecimal.class);
    public static final Field<BigDecimal> EFFECTIVE_SCORE = field(name(T, "effective_score"), BigDecimal.class);
    public static final Field<String> SCORE_SOURCE = field(name(T, "score_source"), String.class);
    public static final Field<String> FALLBACK_REASON = field(name(T, "fallback_reason"), String.class);
    public static final Field<String> EXPLANATION = field(name(T, "explanation"), String.class);
    public static final Field<OffsetDateTime> COMPUTED_AT = field(name(T, "computed_at"), OffsetDateTime.class);

    private PropertyScoreTable() {
    }
}
