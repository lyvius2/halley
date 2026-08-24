package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class UserCriterionScoreTable {

    private static final String T = "user_criterion_score";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<Long> PROPERTY_ID = field(name(T, "property_id"), Long.class);
    public static final Field<Long> USER_ID = field(name(T, "user_id"), Long.class);
    public static final Field<String> CRITERION_CODE = field(name(T, "criterion_code"), String.class);
    public static final Field<Integer> SCORE = field(name(T, "score"), Integer.class);

    private UserCriterionScoreTable() {
    }
}
