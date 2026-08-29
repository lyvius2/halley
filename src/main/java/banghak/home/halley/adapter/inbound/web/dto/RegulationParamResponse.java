package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationValueType;

import java.time.Instant;

/** 규제 파라미터 한 건 (설계 I68). */
public record RegulationParamResponse(
        Long id,
        String profile,
        String paramKey,
        String paramValue,
        RegulationValueType valueType,
        String description,
        Instant updatedAt
) {

    public static RegulationParamResponse from(RegulationParam p) {
        return new RegulationParamResponse(p.id(), p.profile(), p.paramKey(), p.paramValue(),
                p.valueType(), p.description(), p.updatedAt());
    }
}
