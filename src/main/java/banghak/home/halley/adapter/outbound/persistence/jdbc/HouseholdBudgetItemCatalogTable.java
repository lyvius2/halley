package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import java.time.Instant;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class HouseholdBudgetItemCatalogTable {
    private static final String T = "household_budget_item_catalog";
    public static final Table<Record> TABLE = table(name(T));
    public static final Field<Long> ID = field(name(T, "id"), Long.class);
    public static final Field<String> SEED_KEY = field(name(T, "seed_key"), String.class);
    public static final Field<String> CATEGORY = field(name(T, "category"), String.class);
    public static final Field<String> ITEM_NAME = field(name(T, "item_name"), String.class);
    public static final Field<Boolean> REQUIRED = field(name(T, "required"), Boolean.class);
    public static final Field<Boolean> RECOMMENDED = field(name(T, "recommended"), Boolean.class);
    public static final Field<Long> DEFAULT_BUDGET = field(name(T, "default_budget_amount_won"), Long.class);
    public static final Field<String> CANDIDATE_NAME = field(name(T, "candidate_name"), String.class);
    public static final Field<String> CANDIDATE_URL = field(name(T, "candidate_url"), String.class);
    public static final Field<String> ALTERNATIVE_NAME = field(name(T, "alternative_name"), String.class);
    public static final Field<String> ALTERNATIVE_URL = field(name(T, "alternative_url"), String.class);
    public static final Field<String> NOTE = field(name(T, "note"), String.class);
    public static final Field<Object> CREATED_AT = field(name(T, "created_at"), Object.class);
    private HouseholdBudgetItemCatalogTable() { }
}
