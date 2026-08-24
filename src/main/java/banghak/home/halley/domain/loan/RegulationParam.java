package banghak.home.halley.domain.loan;

import java.time.Instant;

public record RegulationParam(
        Long id,
        String profile,
        String paramKey,
        String paramValue,
        RegulationValueType valueType,
        String description,
        Long updatedBy,
        Instant updatedAt
) {
}
