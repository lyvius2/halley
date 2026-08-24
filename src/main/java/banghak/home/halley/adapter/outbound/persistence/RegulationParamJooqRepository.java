package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.RegulationParamRepository;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

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
import static banghak.home.halley.adapter.outbound.persistence.jdbc.RegulationParamTable.VALUE_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class RegulationParamJooqRepository implements RegulationParamRepository {

    private final DSLContext dsl;

    public RegulationParamJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
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
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<RegulationParam> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<RegulationParam> findByProfile(String profile) {
        return dsl.selectFrom(TABLE)
                .where(PROFILE.eq(profile))
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
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
