package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.openbanking.OpenBankingAccount;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.OpenBankingAccountTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class OpenBankingAccountRepository {
    private final DSLContext dsl;

    public OpenBankingAccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public OpenBankingAccount save(OpenBankingAccount account) {
        final Long id = dsl.insertInto(TABLE).set(CONNECTION_ID, account.connectionId())
                .set(FINTECH_USE_NUM_HASH, account.fintechUseNumHash())
                .set(ENCRYPTED_FINTECH_USE_NUM, account.encryptedFintechUseNum())
                .set(FINTECH_USE_NUM_MASKED, account.fintechUseNumMasked()).set(BANK_CODE, account.bankCode())
                .set(BANK_NAME, account.bankName()).set(PRODUCT_NAME, account.productName())
                .set(ACCOUNT_TYPE, account.accountType()).set(LAST_BALANCE_AMT, account.lastBalanceAmount())
                .set(LAST_AVAILABLE_AMT, account.lastAvailableAmount()).set(LAST_SYNCED_AT, toOffset(account.lastSyncedAt()))
                .set(LINKED_BUDGET_ASSET_ID, account.linkedBudgetAssetId()).set(ACTIVE, account.active())
                .returningResult(ID).fetchOne().component1();
        return findByConnectionId(account.connectionId()).stream().filter(saved -> saved.id().equals(id)).findFirst().orElseThrow();
    }

    public List<OpenBankingAccount> findByConnectionId(Long connectionId) {
        return dsl.selectFrom(TABLE).where(CONNECTION_ID.eq(connectionId)).orderBy(ID).fetch().map(this::map);
    }

    private OpenBankingAccount map(Record record) {
        return new OpenBankingAccount(record.get(ID), record.get(CONNECTION_ID), record.get(FINTECH_USE_NUM_HASH),
                record.get(ENCRYPTED_FINTECH_USE_NUM), record.get(FINTECH_USE_NUM_MASKED), record.get(BANK_CODE),
                record.get(BANK_NAME), record.get(PRODUCT_NAME), record.get(ACCOUNT_TYPE), record.get(LAST_BALANCE_AMT),
                record.get(LAST_AVAILABLE_AMT), toInstant(record.get(LAST_SYNCED_AT)), record.get(LINKED_BUDGET_ASSET_ID),
                record.get(ACTIVE), toInstant(record.get(CREATED_AT)), toInstant(record.get(UPDATED_AT)));
    }
}
