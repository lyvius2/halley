package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.geo.LegalDongCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

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

    private static final TypeReference<List<LegalDongCode>> LIST = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ReferenceDataCache cache;

    public LegalDongCodeRepository(DSLContext dsl, ReferenceDataCache cache) {
        this.dsl = dsl;
        this.cache = cache;
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
        cache.evictKeys(CachePort.LEGAL_DONG,
                "code:" + legalDongCode.code(),
                dongKey(legalDongCode.sigungu(), legalDongCode.dongName()));
        return findById(legalDongCode.code()).orElseThrow();
    }

    public Optional<LegalDongCode> findById(String code) {
        return cache.findOne(CachePort.LEGAL_DONG, "code:" + code, LIST, () -> fetchById(code));
    }

    private Optional<LegalDongCode> fetchById(String code) {
        return dsl.selectFrom(TABLE)
                .where(CODE.eq(code))
                .fetchOptional()
                .map(this::map);
    }

 /** 실거래를 볼 때마다 지나는 자리라 담아 둡니다. */
    public Optional<LegalDongCode> findBySigunguAndDong(String sigungu, String dongName) {
        return cache.findOne(CachePort.LEGAL_DONG, dongKey(sigungu, dongName), LIST,
                () -> fetchBySigunguAndDong(sigungu, dongName));
    }

    private Optional<LegalDongCode> fetchBySigunguAndDong(String sigungu, String dongName) {
        return dsl.selectFrom(TABLE)
                .where(SIGUNGU.eq(sigungu).and(DONG_NAME.eq(dongName)).and(IS_ACTIVE.eq(true)))
                .fetchOptional()
                .map(this::map);
    }

 /** 규제지역 매칭에 쓰는 시군구 사전. */
    public List<LegalDongCode> findAll() {
        return dsl.selectFrom(TABLE).fetch().map(this::map);
    }

 /** 시군구 대표코드(뒤 5자리가 0)의 수. 사전이 이미 채워졌는지 보는 값이라, */
    public int countSigungu() {
        return dsl.fetchCount(TABLE, CODE.like("_____00000"));
    }

    public void delete(String code) {
        dsl.deleteFrom(TABLE)
                .where(CODE.eq(code))
                .execute();
        cache.evict(CachePort.LEGAL_DONG);
    }

 /** 키를 만드는 자리는 하나여야 한다. 읽는 쪽과 지우는 쪽이 어긋나면 지운 적이 없는 셈이다 */
    private String dongKey(String sigungu, String dongName) {
        return "dong:" + sigungu + ":" + dongName;
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
