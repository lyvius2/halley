package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.DESCRIPTION;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.PARAM_KEY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.PARAM_VALUE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.PROFILE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.UPDATED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.UPDATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.VALUE_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toOffset;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class RegulationParamRepository {

    private static final TypeReference<List<RegulationParam>> LIST = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ReferenceDataCache cache;

    public RegulationParamRepository(DSLContext dsl, ReferenceDataCache cache) {
        this.dsl = dsl;
        this.cache = cache;
    }

    public RegulationParam save(RegulationParam param) {
        Long id = dsl.insertInto(TABLE)
                .set(PROFILE, param.profile())
                .set(PARAM_KEY, param.paramKey())
                .set(PARAM_VALUE, param.paramValue())
                .set(VALUE_TYPE, param.valueType() == null ? null : param.valueType().name())
                .set(DESCRIPTION, param.description())
                .set(UPDATED_BY, param.updatedBy())
                .returningResult(ID)
                .fetchOne()
                .component1();
        cache.evict(CachePort.REGULATION_PARAM);
        return findById(id).orElseThrow();
    }

    public Optional<RegulationParam> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    /**
     * 대출 계산이 <b>매번</b> 읽는 자리라 담아 둡니다 (설계 I239).
     *
     * <p>키가 프로파일입니다 — 프로파일을 갈아 끼우면 다른 칸을 보게 되므로
     * 전환만으로 옛 값이 섞이지 않습니다. 다만 <b>값을 고치면 반드시 지웁니다</b>:
     * 이 표가 틀리면 LTV·방공제가 틀리고, 그러면 <b>대출 한도가 틀립니다.</b>
     */
    public List<RegulationParam> findByProfile(String profile) {
        return cache.get(CachePort.REGULATION_PARAM, profile, LIST, () -> fetchByProfile(profile));
    }

    private List<RegulationParam> fetchByProfile(String profile) {
        return dsl.selectFrom(TABLE)
                .where(PROFILE.eq(profile))
                .fetch()
                .map(this::map);
    }

    public RegulationParam update(RegulationParam param) {
        dsl.update(TABLE)
                .set(PARAM_VALUE, param.paramValue())
                .set(DESCRIPTION, param.description())
                .set(UPDATED_BY, param.updatedBy())
                .set(UPDATED_AT, toOffset(param.updatedAt()))
                .where(ID.eq(param.id()))
                .execute();
        cache.evict(CachePort.REGULATION_PARAM);   // 한도가 틀리는 것보다 다시 읽는 편이 낫다
        return findById(param.id()).orElseThrow();
    }

    /** 등록된 프로파일 이름 목록 (중복 없이, 이름순). */
    public List<String> findProfiles() {
        return dsl.selectDistinct(PROFILE)
                .from(TABLE)
                .orderBy(PROFILE.asc())
                .fetch(PROFILE);
    }

    /** 프로파일을 통째로 복제한다 — 규제가 바뀌면 새 프로파일을 만들어 옛 값을 남긴다 (설계 I64). */
    public int copyProfile(String from, String to, Long updatedBy) {
        final List<RegulationParam> source = fetchByProfile(from);   // 복제는 원본에서 읽는다
        for (final RegulationParam param : source) {
            save(new RegulationParam(null, to, param.paramKey(), param.paramValue(),
                    param.valueType(), param.description(), updatedBy, null));
        }
        return source.size();
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
        cache.evict(CachePort.REGULATION_PARAM);
    }

    private RegulationParam map(Record r) {
        return new RegulationParam(
                r.get(ID),
                r.get(PROFILE),
                r.get(PARAM_KEY),
                r.get(PARAM_VALUE),
                toEnum(RegulationValueType.class, r.get(VALUE_TYPE)),
                r.get(DESCRIPTION),
                r.get(UPDATED_BY),
                toInstant(r.get(UPDATED_AT))
        );
    }
}
