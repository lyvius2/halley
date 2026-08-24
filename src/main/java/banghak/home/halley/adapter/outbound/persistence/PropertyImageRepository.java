package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.ImageType;
import banghak.home.halley.domain.property.PropertyImage;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.IMAGE_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.SORT_ORDER;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.STORAGE_PATH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyImageTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;

@Repository
public class PropertyImageRepository {

    private final DSLContext dsl;

    public PropertyImageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PropertyImage save(PropertyImage image) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, image.propertyId())
                .set(IMAGE_TYPE, image.imageType() == null ? null : image.imageType().name())
                .set(STORAGE_PATH, image.storagePath())
                .set(SORT_ORDER, image.sortOrder())
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<PropertyImage> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<PropertyImage> findByPropertyId(Long propertyId) {
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

    private PropertyImage map(Record r) {
        return new PropertyImage(
                r.get(ID),
                r.get(PROPERTY_ID),
                toEnum(ImageType.class, r.get(IMAGE_TYPE)),
                r.get(STORAGE_PATH),
                r.get(SORT_ORDER)
        );
    }
}
