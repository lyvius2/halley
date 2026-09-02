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
        // 낡은 칸이 어느 것인지 정확히 안다 — 갈래를 통째로 훑을 이유가 없다 (설계 I239).
        // 사전은 칸이 수만 개라, 한 건 넣을 때마다 전부 훑으면 캐시가 짐이 된다
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

    /**
     * 실거래를 볼 때마다 지나는 자리라 담아 둡니다 (설계 I239).
     *
     * <p><b>못 찾은 것도 담습니다.</b> 사전에 없는 동네가 드물지 않고, 그때마다
     * 원본까지 가면 캐시가 아무 일도 안 합니다 — [I219]에서 실거래에 쓴 처방과 같습니다.
     *
     * <p>{@code findAll} 은 담지 않습니다. 수만 행이라 한 덩어리로 담을 것이 못 되고,
     * 부르는 곳도 규제 고시를 반영하는 관리자 작업 한 군데뿐입니다.
     */
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
        cache.evict(CachePort.LEGAL_DONG);
    }

    /** 키를 만드는 자리는 <b>하나</b>여야 한다 — 읽는 쪽과 지우는 쪽이 어긋나면 지운 적이 없는 셈이다 */
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
