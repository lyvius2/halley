package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ReferenceDealType;
import banghak.home.halley.domain.property.ReferenceSource;
import banghak.home.halley.domain.property.ReferenceTransaction;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.CACHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.CONTRACT_DATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.DEAL_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.FLOOR_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.PRICE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.SOURCE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toLocalDate;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toSqlDate;

@Repository
public class ReferenceTransactionRepository {

    private final DSLContext dsl;

    public ReferenceTransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ReferenceTransaction save(ReferenceTransaction transaction) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, transaction.propertyId())
                .set(DEAL_TYPE, transaction.dealType() == null ? null : transaction.dealType().name())
                .set(CONTRACT_DATE, toSqlDate(transaction.contractDate()))
                .set(PRICE, transaction.price())
                .set(FLOOR_NO, transaction.floorNo())
                .set(SOURCE, transaction.source() == null ? null : transaction.source().name())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<ReferenceTransaction> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<ReferenceTransaction> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private ReferenceTransaction map(Record r) {
        return new ReferenceTransaction(
                r.get(ID),
                r.get(PROPERTY_ID),
                toEnum(ReferenceDealType.class, r.get(DEAL_TYPE)),
                toLocalDate(r.get(CONTRACT_DATE)),
                r.get(PRICE),
                r.get(FLOOR_NO),
                toEnum(ReferenceSource.class, r.get(SOURCE)),
                toInstant(r.get(CACHED_AT))
        );
    }
}
