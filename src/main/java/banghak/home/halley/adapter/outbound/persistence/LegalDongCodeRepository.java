package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.geo.LegalDongCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    public Optional<LegalDongCode> findBySigunguAndDong(String sigungu, String dongName) {
        return dsl.selectFrom(TABLE)
                .where(SIGUNGU.eq(sigungu).and(DONG_NAME.eq(dongName)).and(IS_ACTIVE.eq(true)))
                .fetchOptional()
                .map(this::map);
    }

    /** 규제지역 매칭에 쓰는 시군구 사전 (설계 I78). */
    public List<LegalDongCode> findAll() {
        return dsl.selectFrom(TABLE).fetch().map(this::map);
    }

    /**
     * 시군구 대표코드(뒤 5자리가 0)의 수. 사전이 이미 채워졌는지 보는 값이라,
     * 채워져 있으면 기동할 때마다 V-World를 다시 부르지 않는다 (설계 I78).
     */
    public int countSigungu() {
        return dsl.fetchCount(TABLE, CODE.like("_____00000"));
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
