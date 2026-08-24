package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class CriterionWeightTable {

    private static final String T = "criterion_weight";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> CRITERION_CODE = field(name(T, "criterion_code"), String.class);
    public static final Field<Integer> PRIORITY_RANK = field(name(T, "priority_rank"), Integer.class);
    public static final Field<BigDecimal> WEIGHT = field(name(T, "weight"), BigDecimal.class);
    public static final Field<OffsetDateTime> UPDATED_AT = field(name(T, "updated_at"), OffsetDateTime.class);

    private CriterionWeightTable() {
    }
}
