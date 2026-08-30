package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.PropertyComment;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.AUTHOR_NICKNAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.CONTENT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyCommentTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class PropertyCommentRepository {

    private final DSLContext dsl;

    public PropertyCommentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PropertyComment save(PropertyComment comment) {
        final Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, comment.propertyId())
                .set(USER_ID, comment.userId())
                .set(CONTENT, comment.content())
                .set(CREATED_AT, toOffset(comment.createdAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public PropertyComment update(PropertyComment comment) {
        dsl.update(TABLE)
                .set(CONTENT, comment.content())
                .set(UPDATED_AT, toOffset(comment.updatedAt()))
                .where(ID.eq(comment.id()))
                .execute();
        return findById(comment.id()).orElseThrow();
    }

    public Optional<PropertyComment> findById(Long id) {
        return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map);
    }

    /** 오래된 순 — 대화를 읽듯 위에서 아래로 본다. */
    public List<PropertyComment> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .orderBy(CREATED_AT.asc(), ID.asc())
                .fetch()
                .map(this::map);
    }

    public Optional<PropertyComment> findByPropertyIdAndUserId(Long propertyId, Long userId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)))
                .fetchOptional()
                .map(this::map);
    }

    /** 탈퇴 직전 작성자 이름을 값으로 굳힌다 (설계 I88). */
    public int snapshotAuthorNickname(Long userId, String nickname) {
        return dsl.update(TABLE)
                .set(AUTHOR_NICKNAME, nickname)
                .where(USER_ID.eq(userId))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute();
    }

    private PropertyComment map(Record r) {
        return new PropertyComment(
                r.get(ID), r.get(PROPERTY_ID), r.get(USER_ID), r.get(CONTENT),
                toInstant(r.get(CREATED_AT)), toInstant(r.get(UPDATED_AT)));
    }
}
