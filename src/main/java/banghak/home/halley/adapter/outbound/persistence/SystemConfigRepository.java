package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.CATEGORY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.CONFIG_KEY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.CONFIG_VALUE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.DESCRIPTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.MASKED;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.UPDATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.SystemConfigTable.VALUE_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class SystemConfigRepository {

    private final DSLContext dsl;

    public SystemConfigRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public SystemConfig save(SystemConfig config) {
        dsl.insertInto(TABLE)
                .set(CONFIG_KEY, config.configKey())
                .set(CONFIG_VALUE, config.configValue())
                .set(VALUE_TYPE, config.valueType() == null ? null : config.valueType().name())
                .set(CATEGORY, config.category() == null ? null : config.category().name())
                .set(DESCRIPTION, config.description())
                .set(MASKED, config.masked())
                .set(UPDATED_BY, config.updatedBy())
                .execute();
        return findById(config.configKey()).orElseThrow();
    }

    public void update(SystemConfig config) {
        dsl.update(TABLE)
                .set(CONFIG_VALUE, config.configValue())
                .set(UPDATED_BY, config.updatedBy())
                .where(CONFIG_KEY.eq(config.configKey()))
                .execute();
    }

    public Optional<SystemConfig> findById(String configKey) {
        return dsl.selectFrom(TABLE)
                .where(CONFIG_KEY.eq(configKey))
                .fetchOptional()
                .map(this::map);
    }

    public List<SystemConfig> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(String configKey) {
        dsl.deleteFrom(TABLE)
                .where(CONFIG_KEY.eq(configKey))
                .execute();
    }

    private SystemConfig map(Record r) {
        return new SystemConfig(
                r.get(CONFIG_KEY),
                r.get(CONFIG_VALUE),
                toEnum(ConfigValueType.class, r.get(VALUE_TYPE)),
                toEnum(ConfigCategory.class, r.get(CATEGORY)),
                r.get(DESCRIPTION),
                Boolean.TRUE.equals(r.get(MASKED)),
                r.get(UPDATED_BY),
                toInstant(r.get(UPDATED_AT))
        );
    }
}
