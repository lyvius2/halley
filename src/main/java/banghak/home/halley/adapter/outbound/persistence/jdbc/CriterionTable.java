package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class CriterionTable {

    private static final String T = "criterion";

    public static final Table<Record> TABLE = table(name(T));

    public static final Field<String> CODE = field(name(T, "code"), String.class);
    public static final Field<String> NAME = field(name(T, "name"), String.class);
    public static final Field<String> SCORING_TYPE = field(name(T, "scoring_type"), String.class);
    public static final Field<Boolean> ENABLED = field(name(T, "enabled"), Boolean.class);

    private CriterionTable() {
    }
}
