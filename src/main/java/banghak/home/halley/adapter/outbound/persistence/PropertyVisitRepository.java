package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.itinerary.PropertyVisit;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyVisitTable.VISITED_AT;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class PropertyVisitRepository {

    private final DSLContext dsl;

    public PropertyVisitRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 체크한다 (설계 I197).
     *
     * <p><b>두 번 눌러도 한 줄입니다.</b> 이미 있으면 아무것도 하지 않습니다 —
     * 처음 간 시각을 나중 클릭이 덮으면 기록이 아니라 마지막 클릭 시각이 됩니다.
     *
     * <p>"있으면 넘어가고 없으면 넣는다"를 <b>두 문장으로 쓰지 않습니다.</b>
     * 그 사이에 다른 클릭이 끼면 유니크 제약에 걸립니다. DB가 한 문장으로 판단하게 둡니다.
     */
    public void mark(Long propertyId, Long userId, Instant visitedAt) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, propertyId)
                .set(USER_ID, userId)
                .set(VISITED_AT, toOffset(visitedAt))
                .onConflict(PROPERTY_ID, USER_ID)
                .doNothing()
                .execute();
    }

    /** 체크를 푼다 — 잘못 눌렀을 때다. 행을 지운다. */
    public void clear(Long propertyId, Long userId) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)))
                .execute();
    }

    /**
     * <b>`dsl.fetchExists` 를 쓰지 마십시오 (설계 I197).</b>
     *
     * <p>한 트랜잭션 안에서 같은 조건으로 두 번 부르면 <b>첫 답을 그대로 돌려줍니다</b> —
     * 사이에 행을 넣어도 계속 `false` 입니다. `fetchCount` 와 `fetch()` 는 멀쩡합니다.
     * 이걸로 하루를 썼습니다.
     */
    public boolean exists(Long propertyId, Long userId) {
        return dsl.fetchCount(TABLE, PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId))) > 0;
    }

    /** 이 사람이 가 본 곳 전부. 화면이 체크 상태를 되살릴 때 쓴다. */
    public List<PropertyVisit> findByUser(Long userId) {
        return dsl.selectFrom(TABLE)
                .where(USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

    private PropertyVisit map(Record r) {
        return new PropertyVisit(
                r.get(ID),
                r.get(PROPERTY_ID),
                r.get(USER_ID),
                toInstant(r.get(VISITED_AT)));
    }
}
