package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.group.GroupInvite;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.CREATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.EXPIRES_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.GROUP_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.GroupInviteTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class GroupInviteRepository {

    private final DSLContext dsl;

    public GroupInviteRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * @return 코드가 이미 있으면 false. 코드가 기본키라 <b>동시에 같은 코드를 뽑아도</b>
     *         한 쪽만 성공한다 — 생성 쪽에서 확인하면 그 경우를 놓친다
     */
    public boolean saveIfAbsent(GroupInvite invite) {
        try {
            dsl.insertInto(TABLE)
                    .set(CODE, invite.code())
                    .set(GROUP_ID, invite.groupId())
                    .set(CREATED_BY, invite.createdBy())
                    .set(CREATED_AT, toOffset(invite.createdAt()))
                    .set(EXPIRES_AT, toOffset(invite.expiresAt()))
                    .execute();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<GroupInvite> findByCode(String code) {
        return code == null ? Optional.empty()
                : dsl.selectFrom(TABLE).where(CODE.eq(code)).fetchOptional().map(this::map);
    }

    public void delete(String code) {
        dsl.deleteFrom(TABLE).where(CODE.eq(code)).execute();
    }

    /** 만료된 코드를 치운다. 남겨 두면 같은 문자열을 다시 쓸 수 없다. */
    public int deleteExpired(Instant now) {
        return dsl.deleteFrom(TABLE).where(EXPIRES_AT.lt(toOffset(now))).execute();
    }

    /** 그룹이 사라지면 그 그룹의 초대도 무의미하다. */
    public void deleteByGroupId(Long groupId) {
        dsl.deleteFrom(TABLE).where(GROUP_ID.eq(groupId)).execute();
    }

    private GroupInvite map(Record r) {
        return new GroupInvite(r.get(CODE), r.get(GROUP_ID), r.get(CREATED_BY),
                toInstant(r.get(CREATED_AT)), toInstant(r.get(EXPIRES_AT)));
    }
}
