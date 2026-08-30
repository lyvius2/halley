package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.group.UserGroup;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserGroupTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserGroupTable.CREATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserGroupTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserGroupTable.NAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserGroupTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;

@Repository
public class UserGroupRepository {

    private final DSLContext dsl;

    public UserGroupRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public UserGroup save(UserGroup group) {
        final Long id = dsl.insertInto(TABLE)
                .set(NAME, group.name())
                .set(CREATED_BY, group.createdBy())
                .set(CREATED_AT, toOffset(group.createdAt() == null ? Instant.now() : group.createdAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<UserGroup> findById(Long id) {
        return id == null ? Optional.empty()
                : dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map);
    }

    public List<UserGroup> findAll() {
        return dsl.selectFrom(TABLE).orderBy(ID.asc()).fetch().map(this::map);
    }

    public void rename(Long id, String name) {
        dsl.update(TABLE).set(NAME, name).where(ID.eq(id)).execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute();
    }

    private UserGroup map(Record r) {
        return new UserGroup(r.get(ID), r.get(NAME), r.get(CREATED_BY), toInstant(r.get(CREATED_AT)));
    }
}
