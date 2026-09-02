package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.setting.SystemConfig;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

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

    private static final TypeReference<List<SystemConfig>> LIST = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ReferenceDataCache cache;

    public SystemConfigRepository(DSLContext dsl, ReferenceDataCache cache) {
        this.dsl = dsl;
        this.cache = cache;
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
        cache.evict(CachePort.SYSTEM_CONFIG);
        return findById(config.configKey()).orElseThrow();
    }

    public void update(SystemConfig config) {
        dsl.update(TABLE)
                .set(CONFIG_VALUE, config.configValue())
                .set(UPDATED_BY, config.updatedBy())
                .where(CONFIG_KEY.eq(config.configKey()))
                .execute();
        cache.evict(CachePort.SYSTEM_CONFIG);
    }

    /**
     * 12곳에서 읽습니다 — 특히 {@code loan.regulation.profile} 은 채점·대출·전망이
     * 각각 물어봅니다. 담아 둡니다 (설계 I239).
     *
     * <p>수명을 10분으로 짧게 둔 것은 <b>관리자 화면에서 자주 만지기</b> 때문입니다.
     * 저장 시 지우고 있으니 수명은 안전망일 뿐입니다.
     */
    public Optional<SystemConfig> findById(String configKey) {
        return cache.findOne(CachePort.SYSTEM_CONFIG, configKey, LIST, () -> fetchById(configKey));
    }

    private Optional<SystemConfig> fetchById(String configKey) {
        return dsl.selectFrom(TABLE)
                .where(CONFIG_KEY.eq(configKey))
                .fetchOptional()
                .map(this::map);
    }

    public List<SystemConfig> findAll() {
        return cache.get(CachePort.SYSTEM_CONFIG, ReferenceDataCache.WHOLE, LIST, this::fetchAll);
    }

    private List<SystemConfig> fetchAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(String configKey) {
        dsl.deleteFrom(TABLE)
                .where(CONFIG_KEY.eq(configKey))
                .execute();
        cache.evict(CachePort.SYSTEM_CONFIG);
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
