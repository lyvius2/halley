package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.geo.LegalDongCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.DONG_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.IS_ACTIVE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.RI_NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.SIDO;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.SIGUNGU;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LegalDongCodeTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class LegalDongCodeRepository {

    private final DSLContext dsl;

    public LegalDongCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public LegalDongCode save(LegalDongCode legalDongCode) {
        dsl.insertInto(TABLE)
                .set(CODE, legalDongCode.code())
                .set(SIDO, legalDongCode.sido())
                .set(SIGUNGU, legalDongCode.sigungu())
                .set(DONG_NAME, legalDongCode.dongName())
                .set(RI_NAME, legalDongCode.riName())
                .set(IS_ACTIVE, legalDongCode.isActive())
                .execute();
        return findById(legalDongCode.code()).orElseThrow();
    }

    public Optional<LegalDongCode> findById(String code) {
        return dsl.selectFrom(TABLE)
                .where(CODE.eq(code))
                .fetchOptional()
                .map(this::map);
    }

    public void delete(String code) {
        dsl.deleteFrom(TABLE)
                .where(CODE.eq(code))
                .execute();
    }

    private LegalDongCode map(Record r) {
        return new LegalDongCode(
                r.get(CODE),
                r.get(SIDO),
                r.get(SIGUNGU),
                r.get(DONG_NAME),
                r.get(RI_NAME),
                Boolean.TRUE.equals(r.get(IS_ACTIVE)),
                toInstant(r.get(UPDATED_AT))
        );
    }
}
