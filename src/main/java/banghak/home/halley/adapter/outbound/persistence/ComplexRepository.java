package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.Complex;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.ADDRESS_JIBUN;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.LAT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.LNG;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.MATCH_KEY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.ComplexTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class ComplexRepository {

    private final DSLContext dsl;

    public ComplexRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Complex> findByMatchKey(String matchKey) {
        return dsl.selectFrom(TABLE)
                .where(MATCH_KEY.eq(matchKey))
                .fetchOptional()
                .map(this::map);
    }

    public Optional<Complex> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    /**
     * 없으면 만들고, 있으면 그것을 돌려준다.
     *
     * <p><b>동시에 두 매물이 같은 단지를 만들 수 있습니다.</b> 등록은 배경 보정과
     * 함께 도는 데다, 사람 둘이 같은 단지를 나란히 넣기도 합니다.
     * 유일 인덱스가 막아 주므로, 부딪히면 <b>다시 찾아</b> 돌려줍니다 —
     * 미리 잠그는 것보다 싸고, 놓치는 경우가 없습니다.
     */
    public Complex findOrCreate(String matchKey, Complex candidate) {
        final Optional<Complex> existing = findByMatchKey(matchKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            final Long id = dsl.insertInto(TABLE)
                    .set(MATCH_KEY, matchKey)
                    .set(NAME, candidate.name())
                    .set(ADDRESS_JIBUN, candidate.addressJibun())
                    .set(LAT, candidate.lat())
                    .set(LNG, candidate.lng())
                    .returningResult(ID)
                    .fetchOne()
                    .component1();
            return findById(id).orElseThrow();
        } catch (DuplicateKeyException e) {
            // 그 사이 다른 쪽이 먼저 넣었다. 그쪽 것을 쓴다
            return findByMatchKey(matchKey).orElseThrow(() -> e);
        }
    }

    private Complex map(Record r) {
        return new Complex(
                r.get(ID),
                r.get(MATCH_KEY),
                r.get(NAME),
                r.get(ADDRESS_JIBUN),
                r.get(LAT),
                r.get(LNG),
                toInstant(r.get(CREATED_AT)));
    }
}
