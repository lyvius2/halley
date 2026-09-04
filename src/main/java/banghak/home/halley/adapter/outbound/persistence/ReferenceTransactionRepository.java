package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ReferenceDealType;
import banghak.home.halley.domain.property.ReferenceSource;
import banghak.home.halley.domain.property.ReferenceTransaction;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.CACHED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.CONTRACT_DATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.DEAL_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.FLOOR_NO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.AREA_M2;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.PRICE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ReferenceTransactionTable.COMPLEX_ID;
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
                .set(COMPLEX_ID, transaction.complexId())
                .set(DEAL_TYPE, transaction.dealType() == null ? null : transaction.dealType().name())
                .set(CONTRACT_DATE, toSqlDate(transaction.contractDate()))
                .set(PRICE, transaction.price())
                .set(AREA_M2, transaction.areaM2())
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

    /**
     * 단지와 <b>비슷한 평형</b>으로 찾는다 (설계 I266).
     *
     * <p>예전에는 매물 번호로 찾았습니다. 그러면 같은 단지 같은 평형이라도
     * <b>매물마다 국토부를 다시 불렀습니다.</b>
     *
     * <p>면적은 딱 맞지 않습니다 — 84.93 과 84.98 은 같은 평형입니다.
     * 저장할 때 쓰는 허용 범위와 <b>같은 값</b>이어야 합니다. 다르면
     * 담아 놓고 못 찾는 일이 생깁니다.
     *
     * @param areaM2 null 이면 그 단지 것을 <b>다</b> 돌려준다 — 면적을 모르면 가릴 수 없다
     */
    public List<ReferenceTransaction> findByComplexAndArea(Long complexId, BigDecimal areaM2,
                                                           double tolerance) {
        var condition = COMPLEX_ID.eq(complexId);
        if (areaM2 != null && areaM2.signum() > 0) {
            final BigDecimal factor = BigDecimal.valueOf(tolerance);
            condition = condition.and(AREA_M2.between(
                    areaM2.multiply(BigDecimal.ONE.subtract(factor)),
                    areaM2.multiply(BigDecimal.ONE.add(factor))));
        }
        return dsl.selectFrom(TABLE)
                .where(condition)
                .fetch()
                .map(this::map);
    }

    /** 단지 전체를 지운다 — 다시 받아 올 때 쓴다. */
    public void deleteByComplexId(Long complexId) {
        dsl.deleteFrom(TABLE).where(COMPLEX_ID.eq(complexId)).execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private ReferenceTransaction map(Record r) {
        return new ReferenceTransaction(
                r.get(ID),
                r.get(COMPLEX_ID),
                toEnum(ReferenceDealType.class, r.get(DEAL_TYPE)),
                toLocalDate(r.get(CONTRACT_DATE)),
                r.get(PRICE),
                r.get(AREA_M2),
                r.get(FLOOR_NO),
                toEnum(ReferenceSource.class, r.get(SOURCE)),
                toInstant(r.get(CACHED_AT))
        );
    }
}
