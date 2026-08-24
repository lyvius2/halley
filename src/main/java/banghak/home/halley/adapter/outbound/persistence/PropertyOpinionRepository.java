package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.OpinionType;
import banghak.home.halley.domain.property.PropertyOpinion;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.CONTENT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.OPINION_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.SORT_ORDER;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyOpinionTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;

@Repository
public class PropertyOpinionRepository {

    private final DSLContext dsl;

    public PropertyOpinionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PropertyOpinion save(PropertyOpinion opinion) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, opinion.propertyId())
                .set(USER_ID, opinion.userId())
                .set(OPINION_TYPE, opinion.opinionType() == null ? null : opinion.opinionType().name())
                .set(CONTENT, opinion.content())
                .set(SORT_ORDER, opinion.sortOrder())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<PropertyOpinion> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<PropertyOpinion> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private PropertyOpinion map(Record r) {
        return new PropertyOpinion(
                r.get(ID),
                r.get(PROPERTY_ID),
                r.get(USER_ID),
                toEnum(OpinionType.class, r.get(OPINION_TYPE)),
                r.get(CONTENT),
                r.get(SORT_ORDER)
        );
    }
}
