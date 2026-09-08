package banghak.home.halley.adapter.outbound.persistence.jdbc;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class OpenBankingAccountTable {
    private static final String TABLE_NAME = "open_banking_account";

    public static final Table<Record> TABLE = table(name(TABLE_NAME));
    public static final Field<Long> ID = field(name(TABLE_NAME, "id"), Long.class);
    public static final Field<Long> CONNECTION_ID = field(name(TABLE_NAME, "connection_id"), Long.class);
    public static final Field<String> FINTECH_USE_NUM_HASH = field(name(TABLE_NAME, "fintech_use_num_hash"), String.class);
    public static final Field<String> ENCRYPTED_FINTECH_USE_NUM = field(name(TABLE_NAME, "encrypted_fintech_use_num"), String.class);
    public static final Field<String> FINTECH_USE_NUM_MASKED = field(name(TABLE_NAME, "fintech_use_num_masked"), String.class);
    public static final Field<String> BANK_CODE = field(name(TABLE_NAME, "bank_code"), String.class);
    public static final Field<String> BANK_NAME = field(name(TABLE_NAME, "bank_name"), String.class);
    public static final Field<String> PRODUCT_NAME = field(name(TABLE_NAME, "product_name"), String.class);
    public static final Field<String> ACCOUNT_TYPE = field(name(TABLE_NAME, "account_type"), String.class);
    public static final Field<Long> LAST_BALANCE_AMT = field(name(TABLE_NAME, "last_balance_amt"), Long.class);
    public static final Field<Long> LAST_AVAILABLE_AMT = field(name(TABLE_NAME, "last_available_amt"), Long.class);
    public static final Field<Object> LAST_SYNCED_AT = field(name(TABLE_NAME, "last_synced_at"), Object.class);
    public static final Field<Long> LINKED_BUDGET_ASSET_ID = field(name(TABLE_NAME, "linked_budget_asset_id"), Long.class);
    public static final Field<Boolean> ACTIVE = field(name(TABLE_NAME, "active"), Boolean.class);
    public static final Field<Object> CREATED_AT = field(name(TABLE_NAME, "created_at"), Object.class);
    public static final Field<Object> UPDATED_AT = field(name(TABLE_NAME, "updated_at"), Object.class);

    private OpenBankingAccountTable() {
    }
}
