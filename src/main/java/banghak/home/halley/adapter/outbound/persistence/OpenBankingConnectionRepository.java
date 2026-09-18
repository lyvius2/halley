package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.openbanking.OpenBankingConnection;
import banghak.home.halley.domain.openbanking.OpenBankingProvider;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.OpenBankingConnectionTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class OpenBankingConnectionRepository {
    private final DSLContext dsl;

    public OpenBankingConnectionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public OpenBankingConnection save(OpenBankingConnection connection) {
        final Long id = dsl.insertInto(TABLE)
                .set(USER_ID, connection.userId())
                .set(PROVIDER, connection.provider().name())
                .set(ENCRYPTED_ACCESS_TOKEN, connection.encryptedAccessToken())
                .set(ENCRYPTED_REFRESH_TOKEN, connection.encryptedRefreshToken())
                .set(TOKEN_EXPIRES_AT, toOffset(connection.tokenExpiresAt()))
                .set(CONSENTED_AT, toOffset(connection.consentedAt()))
                .set(DISCONNECTED_AT, toOffset(connection.disconnectedAt()))
                .returningResult(ID).fetchOne().component1();
        return findById(id).orElseThrow();
    }

    public Optional<OpenBankingConnection> findActiveByUserId(Long userId) {
        return dsl.selectFrom(TABLE).where(USER_ID.eq(userId).and(DISCONNECTED_AT.isNull()))
                .fetchOptional().map(this::map);
    }

    public Optional<OpenBankingConnection> findById(Long id) {
        return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map);
    }

    private OpenBankingConnection map(Record record) {
        return new OpenBankingConnection(record.get(ID), record.get(USER_ID),
                OpenBankingProvider.valueOf(record.get(PROVIDER)), record.get(ENCRYPTED_ACCESS_TOKEN),
                record.get(ENCRYPTED_REFRESH_TOKEN), toInstant(record.get(TOKEN_EXPIRES_AT)),
                toInstant(record.get(CONSENTED_AT)), toInstant(record.get(DISCONNECTED_AT)),
                toInstant(record.get(CREATED_AT)), toInstant(record.get(UPDATED_AT)));
    }
}
