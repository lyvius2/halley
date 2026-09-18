package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.openbanking.OpenBankingSyncLog;
import banghak.home.halley.domain.openbanking.OpenBankingSyncStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.OpenBankingSyncLogTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class OpenBankingSyncLogRepository {
    private final DSLContext dsl;

    public OpenBankingSyncLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public OpenBankingSyncLog save(OpenBankingSyncLog syncLog) {
        final Long id = dsl.insertInto(TABLE).set(CONNECTION_ID, syncLog.connectionId())
                .set(REQUESTED_AT, toOffset(syncLog.requestedAt())).set(COMPLETED_AT, toOffset(syncLog.completedAt()))
                .set(STATUS, syncLog.status().name()).set(FAILURE_CODE, syncLog.failureCode())
                .set(ACCOUNT_COUNT, syncLog.accountCount()).returningResult(ID).fetchOne().component1();
        return findByConnectionId(syncLog.connectionId()).stream().filter(saved -> saved.id().equals(id))
                .findFirst().orElseThrow();
    }

    public List<OpenBankingSyncLog> findByConnectionId(Long connectionId) {
        return dsl.selectFrom(TABLE).where(CONNECTION_ID.eq(connectionId)).orderBy(REQUESTED_AT.desc())
                .fetch().map(this::map);
    }

    private OpenBankingSyncLog map(Record record) {
        return new OpenBankingSyncLog(record.get(ID), record.get(CONNECTION_ID), toInstant(record.get(REQUESTED_AT)),
                toInstant(record.get(COMPLETED_AT)), OpenBankingSyncStatus.valueOf(record.get(STATUS)),
                record.get(FAILURE_CODE), record.get(ACCOUNT_COUNT), toInstant(record.get(CREATED_AT)));
    }
}
